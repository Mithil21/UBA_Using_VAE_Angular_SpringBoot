package com.forensic.audit.analysis;

import com.forensic.audit.commons.Metadata;
import com.forensic.audit.uba.UbaTelemetrySnapshot;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import java.util.Collections;
import java.util.Map;

@Service
public class VAEAnalysis {

    // ── Scaler constants ──────────────────────────────────────────────────
    // Extracted from user_behavior_vae.pth after training.
    // Re-paste from Python print_java_scaler_constants() after every retrain.
    // Must have exactly 28 elements each.
    private static final float[] SCALER_MEAN = {
            524.8564331528f, 1198.2330217082f, 0.1499415697f,   72.3612000000f,
            217.6441000000f, 1730.3758223592f,  3.3540666667f,  14.1235613646f,
            12.9109653337f,  44.9056000000f,  108.1911393827f, 82.0260783119f,
            0.6621333333f,   0.8926666667f,   2.9999333333f,  5102.1381333333f,
            37658.1641333333f,   3.3534000000f,  72.3612000000f,  106.5966905141f,
            2.9992006554f,  10.8694666667f,  14.5535333333f,  622.9179429403f,
            408.3808322500f, 2499.3035913371f,  0.0482278026f,   0.2612938127f
    };

    private static final float[] SCALER_SCALE = {
            316.8379102425f, 586.2530298041f, 0.0872344442f,  21.8551977317f,
            122.7204887207f, 249.5109565052f, 0.4782295079f,   3.0808997544f,
            3.4756428563f,   7.3531232802f, 15.8046262617f,  28.9683974421f,
            0.8677822973f,   1.1230076679f,  1.4066271701f, 2247.8360781840f,
            25927.0930465745f,   0.9409791567f, 21.8551977317f,  27.5741502302f,
            2.3267072630f,   7.2762371948f,  7.7724685600f, 183.2586063294f,
            86.8863078992f, 990.6946176289f,  0.0243615289f,   0.1961613948f
    };

    // ── Three-zone decision boundaries ────────────────────────────────────
    // Empirically derived from evaluation results:
    //   Normal users scored 0.88 - 0.97
    //   Bots scored        0.11 - 0.40
    //   Distracted human   0.39 (edge case → review zone)
    //
    // Conservative boundaries give headroom for real-world variance.
    // See dissertation Chapter 5 for full justification.
    private static final float ACCEPT_THRESHOLD = 0.65f;
    private static final float REJECT_THRESHOLD = 0.40f;
    private static final float THRESHOLD         = 2.10f;

    private static final String[] FEATURE_NAMES = {
            "avgFlightTime",      "stdFlightTime",       "backspaceRatio",
            "keystrokeCount",     "medianFlightTime",     "meanClickInterval",
            "clickCount",         "meanMouseDistance",    "stdMouseDistance",
            "mouseEventCount",    "meanMouseInterval",    "pageDwellSeconds",
            "tabSwitchCount",     "windowBlurCount",      "navigationCount",
            "timeBeforeFirstInput","formCompletionTime",  "fieldSwitchCount",
            "keystrokeCount2",    "avgKeyHoldTime",       "typingSpeed",
            "backspaceCount",     "specialKeyCount",      "mouseDistance",
            "avgMouseSpeed",      "maxMouseSpeed",        "clickFrequency",
            "idleTimeRatio"
    };

    private OrtEnvironment environment;
    private OrtSession     session;

    // ── Decision enum ─────────────────────────────────────────────────────
    public enum Decision { ACCEPTED, REVIEW, REJECTED }

    // ── Result record ─────────────────────────────────────────────────────
    public record AnomalyResult(
            float    reconstructionError,
            float    normalProbability,
            Decision decision,
            float[]  rawFeatures
    ) {
        public boolean accepted() { return decision == Decision.ACCEPTED; }
        public boolean review()   { return decision == Decision.REVIEW;   }
        public boolean rejected() { return decision == Decision.REJECTED; }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @PostConstruct
    public void initializeModel() throws OrtException {
        if (SCALER_MEAN.length != 28 || SCALER_SCALE.length != 28) {
            throw new IllegalStateException(
                    "[VAE] Scaler arrays must have 28 elements. " +
                            "Got MEAN=" + SCALER_MEAN.length +
                            " SCALE=" + SCALER_SCALE.length);
        }
        this.environment = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        this.session = environment.createSession(
                "src/main/resources/models/user_behavior_vae.onnx", options);
        System.out.printf("[VAE] Model loaded — input_dim=28  " +
                        "accept=%.2f  reject=%.2f  threshold=%.7f%n",
                ACCEPT_THRESHOLD, REJECT_THRESHOLD, THRESHOLD);
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (session     != null) session.close();
            if (environment != null) environment.close();
        } catch (OrtException e) {
            System.err.println("[VAE] Failed to close ONNX resources: " + e.getMessage());
        }
    }

    // ── Main analysis ─────────────────────────────────────────────────────

    public AnomalyResult analyze(Metadata<?> metadata) {
        float[] raw = extractFeatures(metadata);

        // Debug log — remove in production
        System.out.println("[VAE] Raw features:");
        for (int i = 0; i < raw.length; i++)
            System.out.printf("  [%2d] %-24s = %f%n", i, FEATURE_NAMES[i], raw[i]);

        float[] scaled        = scale(sanitize(raw));
        float[] reconstructed = runInference(new float[][]{scaled})[0];
        float   mse           = mse(scaled, reconstructed);

        // Normalised sigmoid — probability = 0.50 when mse == THRESHOLD
        float normalizedMse = mse / THRESHOLD;
        float probability   = (float)(1.0 / (1.0 + Math.exp(5.0 * (normalizedMse - 1.0))));

        // Three-zone decision
        Decision decision;
        if (probability >= ACCEPT_THRESHOLD) {
            decision = Decision.ACCEPTED;
        } else if (probability < REJECT_THRESHOLD) {
            decision = Decision.REJECTED;
        } else {
            decision = Decision.REVIEW;
        }

        System.out.printf("[VAE] mse=%.6f  normalizedMse=%.4f  " +
                        "probability=%.4f  decision=%s%n",
                mse, normalizedMse, probability, decision);

        return new AnomalyResult(mse, probability, decision, raw);
    }

    // ── Telemetry snapshot builder ────────────────────────────────────────
    // Builds a UbaTelemetrySnapshot from the VAE result + metadata.
    // Called by VaeRequestConsumer before saving to any of the three tables.

    public UbaTelemetrySnapshot buildSnapshot(Metadata<?> metadata,
                                              AnomalyResult result) {
        UbaTelemetrySnapshot snap = new UbaTelemetrySnapshot();

        // VAE result
        snap.setReconstructionError(result.reconstructionError());
        snap.setNormalProbability(result.normalProbability());

        float[] f = result.rawFeatures();

        // Features in index order
        snap.setAvgFlightTime(f[0]);
        snap.setStdFlightTime(f[1]);
        snap.setBackspaceRatio(f[2]);
        snap.setKeystrokeCount((int) f[3]);
        snap.setMedianFlightTime(f[4]);
        snap.setMeanClickInterval(f[5]);
        snap.setClickCount((int) f[6]);
        snap.setMeanMouseDistance(f[7]);
        snap.setStdMouseDistance(f[8]);
        snap.setMouseEventCount((int) f[9]);
        snap.setMeanMouseInterval(f[10]);
        snap.setPageDwellSeconds(f[11]);
        snap.setTabSwitchCount((int) f[12]);
        snap.setWindowBlurCount((int) f[13]);
        snap.setNavigationCount((int) f[14]);
        snap.setTimeBeforeFirstInput((long) f[15]);
        snap.setFormCompletionTime((long) f[16]);
        snap.setFieldSwitchCount((int) f[17]);
        snap.setAvgKeyHoldTime(f[19]);
        snap.setTypingSpeed(f[20]);
        snap.setBackspaceCount((int) f[21]);
        snap.setSpecialKeyCount((int) f[22]);
        snap.setMouseDistance(f[23]);
        snap.setAvgMouseSpeed(f[24]);
        snap.setMaxMouseSpeed(f[25]);
        snap.setClickFrequency(f[26]);
        snap.setIdleTimeRatio(f[27]);

        // Raw event counts
        snap.setRawKeystrokeEventCount(metadata.getKeystrokeCount());
        snap.setRawMouseEventCount(metadata.getMouseEventCount());
        snap.setRawClickCount(metadata.getClickCount());
        snap.setRawNavigationCount(metadata.getNavigationCount());
        snap.setClipboardAttemptCount(
                metadata.getClipboardAttempts() != null
                        ? metadata.getClipboardAttempts().size() : 0);

        // Session metadata
        snap.setSessionId(metadata.getSessionId());
        snap.setIpAddress(metadata.getIpAddress());
        snap.setLocation(metadata.getLocation());

        return snap;
    }

    // ── Inference ─────────────────────────────────────────────────────────

    public float[][] runInference(float[][] behaviorFeatures) {
        try {
            String inputName = session.getInputNames().iterator().next();
            try (OnnxTensor inputTensor =
                         OnnxTensor.createTensor(environment, behaviorFeatures)) {
                Map<String, OnnxTensor> inputs =
                        Collections.singletonMap(inputName, inputTensor);
                try (Result results = session.run(inputs)) {
                    return (float[][]) results.get(0).getValue();
                }
            }
        } catch (OrtException e) {
            throw new RuntimeException("[VAE] Error during ONNX inference", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private float[] sanitize(float[] raw) {
        // Clamp each feature to mean +/- 5 standard deviations.
        // Prevents a single corrupted feature from dominating MSE.
        // e.g. timeBeforeFirstInput=341306ms produced MSE=121319
        float[] out = new float[raw.length];
        for (int i = 0; i < raw.length; i++) {
            float min = SCALER_MEAN[i] - 5f * SCALER_SCALE[i];
            float max = SCALER_MEAN[i] + 5f * SCALER_SCALE[i];
            out[i] = Math.max(min, Math.min(max, raw[i]));
        }
        return out;
    }

    private float[] scale(float[] raw) {
        float[] scaled = new float[raw.length];
        for (int i = 0; i < raw.length; i++)
            scaled[i] = (raw[i] - SCALER_MEAN[i]) / SCALER_SCALE[i];
        return scaled;
    }

    private float mse(float[] original, float[] reconstructed) {
        float sum = 0f;
        for (int i = 0; i < original.length; i++) {
            float diff = original[i] - reconstructed[i];
            sum += diff * diff;
        }
        return sum / original.length;
    }

    private float[] extractFeatures(Metadata<?> metadata) {
        float backspaceRatio = metadata.getKeystrokeCount() > 0
                ? (float) metadata.getBackspaceCount() / metadata.getKeystrokeCount()
                : 0f;

        float medianFlightTime = (float) metadata.getAvgFlightTime();

        float meanClickInterval = 0f;
        var clicks = metadata.getClicks();
        if (clicks != null && clicks.size() > 1) {
            float sum = 0f;
            for (int i = 1; i < clicks.size(); i++)
                sum += clicks.get(i).getTimestamp() - clicks.get(i-1).getTimestamp();
            meanClickInterval = sum / (clicks.size() - 1);
        }

        float meanMouseDistance = 0f, stdMouseDistance = 0f, meanMouseInterval = 0f;
        var mouseEvents = metadata.getMouseEvents();
        if (mouseEvents != null && mouseEvents.size() > 1) {
            int n = mouseEvents.size() - 1;
            float[] distances = new float[n];
            float[] intervals = new float[n];
            for (int i = 1; i <= n; i++) {
                float dx = mouseEvents.get(i).getX() - mouseEvents.get(i-1).getX();
                float dy = mouseEvents.get(i).getY() - mouseEvents.get(i-1).getY();
                distances[i-1] = (float) Math.sqrt(dx*dx + dy*dy);
                intervals[i-1] = mouseEvents.get(i).getTimestamp()
                        - mouseEvents.get(i-1).getTimestamp();
            }
            float dSum = 0f, iSum = 0f;
            for (float d : distances) dSum += d;
            for (float t : intervals) iSum += t;
            meanMouseDistance = dSum / n;
            meanMouseInterval = iSum / n;
            float variance = 0f;
            for (float d : distances)
                variance += (d - meanMouseDistance) * (d - meanMouseDistance);
            stdMouseDistance = (float) Math.sqrt(variance / n);
        }

        float pageDwellSeconds = metadata.getPageDwellTime() / 1000f;

        return new float[]{
                (float) metadata.getAvgFlightTime(),        //  0
                (float) metadata.getStdFlightTime(),        //  1
                backspaceRatio,                             //  2
                metadata.getKeystrokeCount(),               //  3
                medianFlightTime,                           //  4
                meanClickInterval,                          //  5
                metadata.getClickCount(),                   //  6
                meanMouseDistance,                          //  7
                stdMouseDistance,                           //  8
                metadata.getMouseEventCount(),              //  9
                meanMouseInterval,                          // 10
                pageDwellSeconds,                           // 11
                metadata.getTabSwitchCount(),               // 12
                metadata.getWindowBlurCount(),              // 13
                metadata.getNavigationCount(),              // 14
                metadata.getTimeBeforeFirstInput(),         // 15
                metadata.getFormCompletionTime(),           // 16
                metadata.getFieldSwitchCount(),             // 17
                metadata.getKeystrokeCount(),               // 18 intentional duplicate
                (float) metadata.getAvgKeyHoldTime(),       // 19
                (float) metadata.getTypingSpeed(),          // 20
                metadata.getBackspaceCount(),               // 21
                metadata.getSpecialKeyCount(),              // 22
                (float) metadata.getMouseDistance(),        // 23
                (float) metadata.getAvgMouseSpeed(),        // 24
                (float) metadata.getMaxMouseSpeed(),        // 25
                (float) metadata.getClickFrequency(),       // 26
                (float) metadata.getIdleTimeRatio(),        // 27
        };
    }
}