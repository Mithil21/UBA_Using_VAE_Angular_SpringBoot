package com.example.uba_research.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Map;

@Service
public class VAEService {

    @Value("${vae.model.path:C:/Users/mithi/OneDrive/Desktop/UBA Research/uba-research-pythonAI}")
    private String vaeModelPath;

    @Value("${vae.python.executable:python}")
    private String pythonExecutable;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VAEResult analyzeRequest(Object requestData, String timestamp) throws IOException {
        String jsonData = objectMapper.writeValueAsString(requestData);
        
        ProcessBuilder processBuilder = new ProcessBuilder(
            pythonExecutable,
            vaeModelPath + "/vae_model.py",
            jsonData,
            timestamp
        );
        
        processBuilder.directory(new java.io.File(vaeModelPath));
        Process process = processBuilder.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }
        
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("VAE analysis interrupted", e);
        }
        
        return objectMapper.readValue(output.toString(), VAEResult.class);
    }

    public static class VAEResult {
        private boolean isMalicious;
        private double anomalyScore;
        private double threshold;
        private String timestamp;
        private String error;

        public boolean isMalicious() {
            return isMalicious;
        }

        public void setMalicious(boolean malicious) {
            isMalicious = malicious;
        }

        public double getAnomalyScore() {
            return anomalyScore;
        }

        public void setAnomalyScore(double anomalyScore) {
            this.anomalyScore = anomalyScore;
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}