package com.forensic.audit.analysis;

import com.forensic.audit.commons.Metadata;
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

    private static final float[] SCALER_MEAN = {
            524.8564331528f, 1198.2330217082f, 0.1499415697f, 72.3612000000f, 217.6441000000f, 1730.3758223592f, 3.3540666667f, 14.1235613646f, 12.9109653337f, 44.9056000000f, 108.1911393827f, 82.0260783119f, 0.6621333333f, 0.8926666667f, 2.9999333333f, 5102.1381333333f, 37658.1641333333f, 3.3534000000f, 72.3612000000f, 106.5966905141f, 2.9992006554f, 10.8694666667f, 14.5535333333f, 622.9179429403f, 408.3808322500f, 2499.3035913371f, 0.0482278026f, 0.2612938127f
    };
    private static final float[] SCALER_SCALE = {
            316.8379102425f, 586.2530298041f, 0.0872344442f, 21.8551977317f, 122.7204887207f, 249.5109565052f, 0.4782295079f, 3.0808997544f, 3.4756428563f, 7.3531232802f, 15.8046262617f, 28.9683974421f, 0.8677822973f, 1.1230076679f, 1.4066271701f, 2247.8360781840f, 25927.0930465745f, 0.9409791567f, 21.8551977317f, 27.5741502302f, 2.3267072630f, 7.2762371948f, 7.7724685600f, 183.2586063294f, 86.8863078992f, 990.6946176289f, 0.0243615289f, 0.1961613948f
    };
    private static final float THRESHOLD = 3.3005989f;

    private OrtEnvironment environment;
    private OrtSession session;

    @PostConstruct
    public void initializeModel() throws OrtException {
        this.environment = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        this.session = environment.createSession("src/main/resources/models/user_behavior_vae.onnx", options);
    }

    public record AnomalyResult(float reconstructionError, float normalProbability, boolean accepted) {}

    public AnomalyResult analyze(Metadata<?> metadata) {
        float[] raw = extractFeatures(metadata);
        String[] names = {
            "avgFlightTime","stdFlightTime","backspaceRatio","keystrokeCount","medianFlightTime",
            "meanClickInterval","clickCount","meanMouseDistance","stdMouseDistance","mouseEventCount",
            "meanMouseInterval","pageDwellSeconds","tabSwitchCount","windowBlurCount","navigationCount",
            "timeBeforeFirstInput","formCompletionTime","fieldSwitchCount","keystrokeCount2",
            "avgKeyHoldTime","typingSpeed","backspaceCount","specialKeyCount",
            "mouseDistance","avgMouseSpeed","maxMouseSpeed","clickFrequency"
        };
        System.out.println("[VAE] Raw features:");
        for (int i = 0; i < raw.length; i++)
            System.out.printf("  [%d] %-22s = %f%n", i, names[i], raw[i]);
        float[] scaled = scale(raw);
        float[] reconstructed = runInference(new float[][]{scaled})[0];
        float mse = mse(scaled, reconstructed);
        float probability = (float) (1.0 / (1.0 + Math.exp(mse - THRESHOLD)));
        return new AnomalyResult(mse, probability, probability >= 0.65f);
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
        // Derived: backspace_ratio
        float backspaceRatio = metadata.getKeystrokeCount() > 0
                ? (float) metadata.getBackspaceCount() / metadata.getKeystrokeCount() : 0f;

        // Derived: median_flight_time — approximate as avgFlightTime when not available
        float medianFlightTime = (float) metadata.getAvgFlightTime();

        // Derived: mean_click_interval from clicks list
        float meanClickInterval = 0f;
        var clicks = metadata.getClicks();
        if (clicks != null && clicks.size() > 1) {
            float sum = 0f;
            for (int i = 1; i < clicks.size(); i++)
                sum += clicks.get(i).getTimestamp() - clicks.get(i - 1).getTimestamp();
            meanClickInterval = sum / (clicks.size() - 1);
        }

        // Derived: mean_mouse_distance, std_mouse_distance, mean_mouse_interval
        float meanMouseDistance = 0f, stdMouseDistance = 0f, meanMouseInterval = 0f;
        var mouseEvents = metadata.getMouseEvents();
        if (mouseEvents != null && mouseEvents.size() > 1) {
            float[] distances = new float[mouseEvents.size() - 1];
            float[] intervals = new float[mouseEvents.size() - 1];
            for (int i = 1; i < mouseEvents.size(); i++) {
                float dx = mouseEvents.get(i).getX() - mouseEvents.get(i - 1).getX();
                float dy = mouseEvents.get(i).getY() - mouseEvents.get(i - 1).getY();
                distances[i - 1] = (float) Math.sqrt(dx * dx + dy * dy);
                intervals[i - 1] = mouseEvents.get(i).getTimestamp() - mouseEvents.get(i - 1).getTimestamp();
            }
            float dSum = 0f, iSum = 0f;
            for (float d : distances) dSum += d;
            for (float t : intervals) iSum += t;
            meanMouseDistance = dSum / distances.length;
            meanMouseInterval = iSum / intervals.length;
            float variance = 0f;
            for (float d : distances) variance += (d - meanMouseDistance) * (d - meanMouseDistance);
            stdMouseDistance = (float) Math.sqrt(variance / distances.length);
        }

        float pageDwellSeconds = metadata.getPageDwellTime() / 1000f;

        // timeBeforeFirstInput was always 0 in training data — zero it out to avoid scaling explosion
        // meanClickInterval from training data is in raw ms (can be negative), keep as-is
        // 27 features in exact training order (matches data_generator.py extract_features)
        return new float[]{
            (float) metadata.getAvgFlightTime(),
            (float) metadata.getStdFlightTime(),
            backspaceRatio,
            metadata.getKeystrokeCount(),
            medianFlightTime,
            meanClickInterval,
            metadata.getClickCount(),
            meanMouseDistance,
            stdMouseDistance,
            metadata.getMouseEventCount(),
            meanMouseInterval,
            pageDwellSeconds,
            metadata.getTabSwitchCount(),
            metadata.getWindowBlurCount(),
            metadata.getNavigationCount(),
            0f,                                  // timeBeforeFirstInput: always 0 in training
            metadata.getFormCompletionTime(),
            metadata.getFieldSwitchCount(),
            metadata.getKeystrokeCount(),
            (float) metadata.getAvgKeyHoldTime(),
            (float) metadata.getTypingSpeed(),
            metadata.getBackspaceCount(),
            metadata.getSpecialKeyCount(),
            (float) metadata.getMouseDistance(),
            (float) metadata.getAvgMouseSpeed(),
            (float) metadata.getMaxMouseSpeed(),
            (float) metadata.getClickFrequency()
        };
    }

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
            throw new RuntimeException("Error during ONNX inference execution", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (session != null) session.close();
            if (environment != null) environment.close();
        } catch (OrtException e) {
            System.err.println("Failed to cleanly close ONNX resources: " + e.getMessage());
        }
    }
}
