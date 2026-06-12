package com.forensic.audit.analysis;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import com.forensic.audit.commons.Metadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
public class VAEAnalysis {

    // -----------------------------------------------------------------------
    // Scaler constants — extracted from user_behavior_vae.pth after training.
    // Re-paste these from Python's print_java_scaler_constants() after every retrain.
    // Must have exactly 28 elements each (one per feature).
    // -----------------------------------------------------------------------
    private static final float[] SCALER_MEAN = {
            524.8564331528f, 1198.2330217082f, 0.1499415697f,   72.3612000000f,
            217.6441000000f, 1730.3758223592f,    3.3540666667f, 14.1235613646f,
            12.9109653337f,   44.9056000000f,  108.1911393827f, 82.0260783119f,
            0.6621333333f,    0.8926666667f,    2.9999333333f,  5102.1381333333f,
            37658.1641333333f,    3.3534000000f,   72.3612000000f,  106.5966905141f,
            2.9992006554f,   10.8694666667f,   14.5535333333f,  622.9179429403f,
            408.3808322500f, 2499.3035913371f,    0.0482278026f,    0.2612938127f
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

    // ROC-optimal threshold from Python evaluate_with_roc() (Youden J statistic).
    // Re-paste from print_java_scaler_constants() after every retrain.
    private static final float THRESHOLD = 4.1433043f;

    // Feature names — must match extractFeatures() return order exactly.
    // Used only for debug logging.
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

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @PostConstruct
    public void initializeModel() throws OrtException {
        // Fail fast — catch scaler/feature count mismatches at startup
        if (SCALER_MEAN.length != 28 || SCALER_SCALE.length != 28) {
            throw new IllegalStateException(
                    "[VAE] Scaler arrays must have 28 elements. " +
                            "Got MEAN=" + SCALER_MEAN.length + "  SCALE=" + SCALER_SCALE.length
            );
        }
        if (FEATURE_NAMES.length != 28) {
            throw new IllegalStateException(
                    "[VAE] FEATURE_NAMES must have 28 elements. Got " + FEATURE_NAMES.length
            );
        }

        this.environment = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        this.session = environment.createSession(
                "src/main/resources/models/user_behavior_vae.onnx", options
        );

        System.out.printf("[VAE] Model loaded — input_dim=28  threshold=%.7f%n", THRESHOLD);
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

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    private float[] sanitize(float[] raw) {
        float[] out = new float[raw.length];
        for (int i = 0; i < raw.length; i++) {
            float min = SCALER_MEAN[i] - 5f * SCALER_SCALE[i];
            float max = SCALER_MEAN[i] + 5f * SCALER_SCALE[i];
            out[i] = Math.max(min, Math.min(max, raw[i]));
        }
        return out;
    }

    public record AnomalyResult(float reconstructionError,
                                float normalProbability,
                                boolean accepted) {}

    public AnomalyResult analyze(Metadata<?> metadata) {
        float[] raw = extractFeatures(metadata);

        // Debug log — remove in production if too noisy
        System.out.println("[VAE] Raw features:");
        for (int i = 0; i < raw.length; i++)
            System.out.printf("  [%2d] %-24s = %f%n", i, FEATURE_NAMES[i], raw[i]);

        float[] scaled = scale(sanitize(raw));
        float[] reconstructed = runInference(new float[][]{scaled})[0];
        float   mse           = mse(scaled, reconstructed);

        // Normalised sigmoid — probability = 1.0 when mse=0, 0.5 when mse=THRESHOLD,
        // approaches 0.0 as mse grows beyond THRESHOLD.
        // Steepness factor 5.0 gives a sharp but not binary transition.
        float normalizedMse = mse / THRESHOLD;
        float probability   = (float) (1.0 / (1.0 + Math.exp(5.0 * (normalizedMse - 1.0))));

        System.out.printf("[VAE] mse=%.6f  normalizedMse=%.4f  probability=%.4f  accepted=%b%n",
                mse, normalizedMse, probability, probability >= 0.50f);

        // Accepted when probability >= 0.50 (i.e. mse <= THRESHOLD)
        return new AnomalyResult(mse, probability, probability >= 0.50f);
    }

    // -----------------------------------------------------------------------
    // Inference
    // -----------------------------------------------------------------------

    public float[][] runInference(float[][] behaviorFeatures) {
        try {
            String inputName = session.getInputNames().iterator().next();
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(environment, behaviorFeatures)) {
                Map<String, OnnxTensor> inputs = Collections.singletonMap(inputName, inputTensor);
                try (Result results = session.run(inputs)) {
                    return (float[][]) results.get(0).getValue();
                }
            }
        } catch (OrtException e) {
            throw new RuntimeException("[VAE] Error during ONNX inference", e);
        }
    }

    // -----------------------------------------------------------------------
    // Feature extraction — 28 features, must match Python extract_features()
    // order exactly. Update both sides together after any schema change.
    // -----------------------------------------------------------------------

    private float[] extractFeatures(Metadata<?> metadata) {

        // --- Derived: backspaceRatio ---
        float backspaceRatio = metadata.getKeystrokeCount() > 0
                ? (float) metadata.getBackspaceCount() / metadata.getKeystrokeCount()
                : 0f;

        // --- Derived: medianFlightTime ---
        // Approximated as avgFlightTime when raw keystroke list is unavailable.
        float medianFlightTime = (float) metadata.getAvgFlightTime();

        // --- Derived: meanClickInterval ---
        float meanClickInterval = 0f;
        var clicks = metadata.getClicks();
        if (clicks != null && clicks.size() > 1) {
            float sum = 0f;
            for (int i = 1; i < clicks.size(); i++)
                sum += clicks.get(i).getTimestamp() - clicks.get(i - 1).getTimestamp();
            meanClickInterval = sum / (clicks.size() - 1);
        }

        // --- Derived: meanMouseDistance, stdMouseDistance, meanMouseInterval ---
        float meanMouseDistance = 0f, stdMouseDistance = 0f, meanMouseInterval = 0f;
        var mouseEvents = metadata.getMouseEvents();
        if (mouseEvents != null && mouseEvents.size() > 1) {
            int   n         = mouseEvents.size() - 1;
            float[] distances = new float[n];
            float[] intervals = new float[n];

            for (int i = 1; i <= n; i++) {
                float dx = mouseEvents.get(i).getX() - mouseEvents.get(i - 1).getX();
                float dy = mouseEvents.get(i).getY() - mouseEvents.get(i - 1).getY();
                distances[i - 1] = (float) Math.sqrt(dx * dx + dy * dy);
                intervals[i - 1] = mouseEvents.get(i).getTimestamp()
                        - mouseEvents.get(i - 1).getTimestamp();
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

        // --- Scalar conversions ---
        float pageDwellSeconds = metadata.getPageDwellTime() / 1000f;

        // ---------------------------------------------------------------
        // 28-feature return array.
        // Order must exactly match Python data_generator.py extract_features()
        // and the SCALER_MEAN / SCALER_SCALE arrays above.
        // ---------------------------------------------------------------
        return new float[]{
                (float) metadata.getAvgFlightTime(),        //  0 avgFlightTime
                (float) metadata.getStdFlightTime(),        //  1 stdFlightTime
                backspaceRatio,                             //  2 backspaceRatio
                metadata.getKeystrokeCount(),               //  3 keystrokeCount
                medianFlightTime,                           //  4 medianFlightTime
                meanClickInterval,                          //  5 meanClickInterval
                metadata.getClickCount(),                   //  6 clickCount
                meanMouseDistance,                          //  7 meanMouseDistance
                stdMouseDistance,                           //  8 stdMouseDistance
                metadata.getMouseEventCount(),              //  9 mouseEventCount
                meanMouseInterval,                          // 10 meanMouseInterval
                pageDwellSeconds,                           // 11 pageDwellSeconds
                metadata.getTabSwitchCount(),               // 12 tabSwitchCount
                metadata.getWindowBlurCount(),              // 13 windowBlurCount
                metadata.getNavigationCount(),              // 14 navigationCount
                metadata.getTimeBeforeFirstInput(),         // 15 timeBeforeFirstInput
                metadata.getFormCompletionTime(),           // 16 formCompletionTime
                metadata.getFieldSwitchCount(),             // 17 fieldSwitchCount
                metadata.getKeystrokeCount(),               // 18 keystrokeCount2 (intentional duplicate)
                (float) metadata.getAvgKeyHoldTime(),       // 19 avgKeyHoldTime
                (float) metadata.getTypingSpeed(),          // 20 typingSpeed
                metadata.getBackspaceCount(),               // 21 backspaceCount
                metadata.getSpecialKeyCount(),              // 22 specialKeyCount
                (float) metadata.getMouseDistance(),        // 23 mouseDistance
                (float) metadata.getAvgMouseSpeed(),        // 24 avgMouseSpeed
                (float) metadata.getMaxMouseSpeed(),        // 25 maxMouseSpeed
                (float) metadata.getClickFrequency(),       // 26 clickFrequency
                (float) metadata.getIdleTimeRatio(),        // 27 idleTimeRatio
        };
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
}