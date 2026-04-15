// src/main/java/com/forensic/audit/filter/UbaDecryptionFilter.java
package com.forensic.audit.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forensic.audit.crypto.CryptoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class UbaDecryptionFilter extends OncePerRequestFilter {

    @Autowired
    private CryptoService cryptoService;
    @Autowired
    private  ObjectMapper objectMapper;

//    public UbaDecryptionFilter(CryptoService cryptoService, ObjectMapper objectMapper) {
//        this.cryptoService = cryptoService;
//        this.objectMapper = objectMapper;
//    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only intercept auth endpoints
        return !path.equals("/api/auth/login") && !path.equals("/api/auth/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Wrap early so the body can be re-read by the downstream controller
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);

        try {
            // Force the body to be cached by reading it now
            byte[] bodyBytes = wrapped.getInputStream().readAllBytes();
            JsonNode body = objectMapper.readTree(bodyBytes);

            String encryptedData   = body.path("encryptedData").asText(null);
            String encryptedAesKey = body.path("encryptedAesKey").asText(null);
            String iv              = body.path("iv").asText(null);

            if (encryptedData != null && encryptedAesKey != null && iv != null) {
                String decryptedJson = cryptoService.decryptUbaEnvelope(encryptedData, encryptedAesKey, iv);
//                log.debug("[UBA Telemetry] Decrypted: {}", decryptedJson);
                wrapped.setAttribute("ubaTelemetry", decryptedJson);
            } else {
                System.out.println("[UBA Filter] Missing encrypted fields on {}; skipping decryption."+ request.getRequestURI());
//                log.warn("[UBA Filter] Missing encrypted fields on {}; skipping decryption.", request.getRequestURI());
            }

        } catch (Exception e) {
            // Non-fatal: log and continue — auth logic should not be blocked by telemetry failure
//            log.error("[UBA Filter] Decryption failed: {}", e.getMessage());
        }

        chain.doFilter(wrapped, response);
    }
}
