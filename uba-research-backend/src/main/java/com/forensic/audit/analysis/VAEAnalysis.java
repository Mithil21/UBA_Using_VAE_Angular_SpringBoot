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

    // Extracted from user_behavior_vae.pth — StandardScaler params used during training
    private static final float[] SCALER_MEAN = {
        437.7084852814f, 1081.4987550125f, 0.1500029017f,   140.5984000000f,  172.7448000000f,
        -1.6506826010f,  5.5076000000f,   359.3819239471f,  173.5247719803f,  42.6202000000f,
        450.0229448303f, 69.5027867840f,  0.9868000000f,    1.4874000000f,    0.0f,
        0.0f,            69502.7868000000f, 0.0f,           70.2992000000f,   89.6286261581f,
        1.2679543908f,   10.5484000000f,  10.5484000000f,   14957.0775034180f, 1067.8971871948f,
        4267.9288916504f, 0.0991368979f
    };
    private static final float[] SCALER_SCALE = {
        145.4375282259f, 390.5250476572f, 0.0432793518f,   35.1922991213f,   36.7505438458f,
        479.3092445987f, 1.7185872803f,  31.3776455959f,   18.4784036040f,   10.4309708062f,
        32.7257188883f,  28.8982196421f, 0.8172060695f,    1.1116839659f,    1.0f,
        1.0f,            28898.2196646531f, 1.0f,          17.5961495606f,    3.7213012798f,
        0.7719612084f,   3.9607647544f,  3.9607647544f,    3946.9970716433f,  156.8428934465f,
        1179.5865908506f, 0.0637269241f
    };
    private static final float THRESHOLD = 0.7554988f;

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
