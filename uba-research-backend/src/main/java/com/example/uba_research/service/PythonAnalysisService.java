package com.example.uba_research.service;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;


@Service
public class PythonAnalysisService {

    private final RestTemplate restTemplate;
    private final String analysisUrl;

    public PythonAnalysisService(@Value("${python.analysis.url:http://localhost:5000/analyze}") String analysisUrl) {
        this.restTemplate = new RestTemplate();
        this.analysisUrl = analysisUrl;
    }


    /**
     * Sends body and metadata to the configured Python analysis endpoint.
     * Expects the service to return a plain string like "Threat" or "Safe".
     */
    public String analyze(String body, String metadata) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = Map.of(
                    "body", body != null ? body : "",
                    "metadata", metadata != null ? metadata : ""
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(analysisUrl, request, String.class);
            String verdict = response.getBody();
            return verdict != null ? verdict.trim() : "Safe";
        } catch (Exception e) {
            System.err.println("PythonAnalysisService error: " + e.getMessage());
            return "Safe";
        }
    }

    public boolean detectMalware(Map<String, Object> indicators) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(indicators);
            
            ProcessBuilder pb = new ProcessBuilder("python", "malware_detector.py", jsonPayload);
            pb.directory(new java.io.File("C:/Users/mithi/OneDrive/Desktop/UBA Research/uba-research-pythonAI"));
            
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            
            process.waitFor();
            return "true".equals(result);
            
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> analyzeBehavior(Map<String, Object> behaviorData) {
        try {
            // Call Python script for behavior analysis
            ProcessBuilder pb = new ProcessBuilder("python", "analyze_behavior.py");
            pb.directory(new java.io.File("C:/Users/mithi/OneDrive/Desktop/UBA Research/uba-research-pythonAI"));
            
            Process process = pb.start();
            
            // Send behavior data to Python script via stdin as JSON
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            java.io.PrintWriter writer = new java.io.PrintWriter(process.getOutputStream());
            writer.println(mapper.writeValueAsString(behaviorData));
            writer.flush();
            writer.close();
            
            // Read Python script output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return Map.of(
                    "analysis", output.toString(),
                    "suspicious", false,
                    "confidence", 0.95
                );
            } else {
                return Map.of(
                    "analysis", "Analysis failed",
                    "suspicious", false,
                    "confidence", 0.0
                );
            }
            
        } catch (Exception e) {
            System.err.println("Python analysis failed: " + e.getMessage());
            return Map.of(
                "analysis", "Analysis unavailable",
                "suspicious", false,
                "confidence", 0.0
            );
        }
    }
}