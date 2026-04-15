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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class UbaDecryptionFilter extends OncePerRequestFilter {

    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/auth/login") && !path.equals("/api/auth/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);

        try {
            byte[] bodyBytes = wrapped.getInputStream().readAllBytes();
            JsonNode body = objectMapper.readTree(bodyBytes);

            String encryptedData   = body.path("encryptedData").asText(null);
            String encryptedAesKey = body.path("encryptedAesKey").asText(null);
            String iv              = body.path("iv").asText(null);

            if (encryptedData != null && encryptedAesKey != null && iv != null) {
                String decryptedJson = cryptoService.decryptUbaEnvelope(encryptedData, encryptedAesKey, iv);
                System.out.println("[UBA Telemetry] Decrypted: " + decryptedJson);
                wrapped.setAttribute("ubaTelemetry", decryptedJson);
            } else {
                log.warn("[UBA Filter] Missing encrypted fields on {}; skipping decryption.", request.getRequestURI());
            }

        } catch (Exception e) {
            log.error("[UBA Filter] Decryption failed on {}: {}", request.getRequestURI(), e.getMessage());
        }

        chain.doFilter(wrapped, response);
    }
}
