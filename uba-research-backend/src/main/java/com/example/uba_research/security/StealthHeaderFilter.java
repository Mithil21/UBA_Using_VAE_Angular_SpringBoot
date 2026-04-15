package com.example.uba_research.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;

@Component
public class StealthHeaderFilter extends OncePerRequestFilter {

    private static final String MASTER_KEY = "HACKATHON_MASTER_KEY_2025";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader("X-Request-ID");
        String sessionToken = request.getHeader("X-Session-Token");
        String clientVersion = request.getHeader("X-Client-Version");
        String traceId = request.getHeader("X-Trace-ID");

        if (requestId != null && sessionToken != null && clientVersion != null && traceId != null) {
            // Try to reconstruct the original encrypted metadata
            String encryptedMetadata = requestId + sessionToken + clientVersion + traceId;
            try {
                String decryptedMetadata = decryptMetadata(encryptedMetadata);
                request.setAttribute("DECODED_METADATA", decryptedMetadata);
            } catch (Exception e) {
                // Set empty metadata if decryption fails
                request.setAttribute("DECODED_METADATA", "{}");
            }
        } else {
            // Set empty metadata if headers are missing
            request.setAttribute("DECODED_METADATA", "{}");
        }

        filterChain.doFilter(request, response);
    }

    private String decryptMetadata(String encrypted) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encrypted);
            String decodedString = new String(decodedBytes);
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < decodedString.length(); i++) {
                result.append((char) (decodedString.charAt(i) ^ MASTER_KEY.charAt(i % MASTER_KEY.length())));
            }

            return result.toString();
        } catch (Exception e) {
            // Return empty JSON if decryption fails
            return "{}";
        }
    }
}