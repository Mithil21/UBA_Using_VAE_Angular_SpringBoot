package com.example.uba_research.security;

import com.example.uba_research.security.exception.MaliciousInputException;
import com.example.uba_research.security.service.VAEService;
import com.example.uba_research.security.util.CachedBodyHttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class RequestInterceptor implements HandlerInterceptor {

    @Autowired
    private VAEService vaeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip VAE analysis for GET requests and health checks
        if ("GET".equals(request.getMethod()) || request.getRequestURI().contains("/health")) {
            return true;
        }

        try {
            String requestBody = "";
            String timestamp = Instant.now().toString();

            // Extract request body if it's a cached request
            if (request instanceof CachedBodyHttpServletRequest cachedRequest) {
                requestBody = new String(cachedRequest.getCachedBody());
            }

            // Prepare data for VAE analysis
            Map<String, Object> analysisData = new HashMap<>();
            analysisData.put("method", request.getMethod());
            analysisData.put("uri", request.getRequestURI());
            analysisData.put("userAgent", request.getHeader("User-Agent"));
            analysisData.put("contentType", request.getContentType());
            analysisData.put("contentLength", request.getContentLength());
            
            // Add request body if present
            if (requestBody != null && !requestBody.isEmpty()) {
                try {
                    Object jsonBody = objectMapper.readValue(requestBody, Object.class);
                    analysisData.put("body", jsonBody);
                } catch (Exception e) {
                    analysisData.put("bodyLength", requestBody.length());
                }
            }

            // Call VAE model for analysis
            VAEService.VAEResult result = vaeService.analyzeRequest(analysisData, timestamp);

            // Check if input is malicious
            if (result.isMalicious()) {
                throw new MaliciousInputException(
                    "Input detected as malicious by VAE model. Anomaly score: " + result.getAnomalyScore(),
                    result.getAnomalyScore(),
                    timestamp
                );
            }

            return true;

        } catch (MaliciousInputException e) {
            throw e;
        } catch (Exception e) {
            // Log error but don't block request for non-critical failures
            System.err.println("VAE analysis failed: " + e.getMessage());
            return true;
        }
    }
}