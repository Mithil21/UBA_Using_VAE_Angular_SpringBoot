//package com.forensic.audit.filter;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.forensic.audit.crypto.CryptoService;
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//import org.springframework.web.util.ContentCachingRequestWrapper;
//
//import java.io.IOException;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class UbaDecryptionFilter extends OncePerRequestFilter {
//
//    @Autowired
//    private CryptoService cryptoService;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    private static Logger log = LoggerFactory.getLogger(UbaDecryptionFilter.class);
//
//    @Override
//    protected boolean shouldNotFilter(HttpServletRequest request) {
//        String path = request.getRequestURI();
//        return !path.equals("/api/auth/login") && !path.equals("/api/auth/register");
//    }
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain chain) throws ServletException, IOException {
//
//        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
//
//        try {
//            byte[] bodyBytes = wrapped.getInputStream().readAllBytes();
//            JsonNode body = objectMapper.readTree(bodyBytes);
//
//            String encryptedData   = body.path("encryptedData").asText(null);
//            String encryptedAesKey = body.path("encryptedAesKey").asText(null);
//            String iv              = body.path("iv").asText(null);
//
//            if (encryptedData != null && encryptedAesKey != null && iv != null) {
//                String decryptedJson = cryptoService.decryptUbaEnvelope(encryptedData, encryptedAesKey, iv);
//                System.out.println("[UBA Telemetry] Decrypted: " + decryptedJson);
//                wrapped.setAttribute("ubaTelemetry", decryptedJson);
//            } else {
//                log.warn("[UBA Filter] Missing encrypted fields on {}; skipping decryption.", request.getRequestURI());
//            }
//
//        } catch (Exception e) {
//            log.error("[UBA Filter] Decryption failed on {}: {}", request.getRequestURI(), e.getMessage());
//        }
//
//        chain.doFilter(wrapped, response);
//    }
//}


package com.forensic.audit.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forensic.audit.crypto.CryptoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

@Slf4j
@Component
@RequiredArgsConstructor
public class UbaDecryptionFilter extends OncePerRequestFilter {

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private ObjectMapper objectMapper;

    private static Logger log = LoggerFactory.getLogger(UbaDecryptionFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/auth/login") && !path.equals("/api/auth/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // 1. Wrap the request to cache the body bytes safely
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        try {
            // 2. Read from our safe cached wrapper
            byte[] bodyBytes = wrappedRequest.getCachedBody();
            JsonNode body = objectMapper.readTree(bodyBytes);

            String encryptedData   = body.path("metadata").path("encryptedData").asText(null);
            String encryptedAesKey = body.path("metadata").path("encryptedAesKey").asText(null);
            String iv              = body.path("metadata").path("iv").asText(null);

            if (encryptedData != null && encryptedAesKey != null && iv != null) {
                String decryptedJson = cryptoService.decryptUbaEnvelope(encryptedData, encryptedAesKey, iv);
                System.out.println("\n====== DECRYPTED UBA METADATA ======");
                System.out.println(decryptedJson);
                System.out.println("====================================\n");

                wrappedRequest.setAttribute("ubaTelemetry", decryptedJson);
            } else {
                log.warn("[UBA Filter] Missing encrypted metadata fields; skipping decryption.");
            }

        } catch (Exception e) {
            log.error("[UBA Filter] Decryption failed: {}", e.getMessage());
        }

        // 3. Pass the wrapped request down the chain so AuthController can still read it
        chain.doFilter(wrappedRequest, response);
    }

    // --- INNER CLASS: Custom Wrapper to prevent stream draining ---
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        public byte[] getCachedBody() {
            return this.cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() { return byteArrayInputStream.available() == 0; }
                @Override
                public boolean isReady() { return true; }
                @Override
                public void setReadListener(ReadListener readListener) { throw new UnsupportedOperationException(); }
                @Override
                public int read() { return byteArrayInputStream.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(this.getInputStream()));
        }
    }
}