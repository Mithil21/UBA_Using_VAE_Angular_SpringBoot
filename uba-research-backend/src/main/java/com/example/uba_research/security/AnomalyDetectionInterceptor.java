package com.example.uba_research.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.uba_research.service.PythonAnalysisService;

import java.nio.charset.StandardCharsets;

@Component
public class AnomalyDetectionInterceptor implements HandlerInterceptor {

    private final PythonAnalysisService pythonAnalysisService;

    public AnomalyDetectionInterceptor(PythonAnalysisService pythonAnalysisService) {
        this.pythonAnalysisService = pythonAnalysisService;
    }

    @Override
    @Valid
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String metadata = (String) request.getAttribute("DECODED_METADATA");
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        String charset = request.getCharacterEncoding() != null ? request.getCharacterEncoding() : StandardCharsets.UTF_8.name();
        String body = new String(bodyBytes, charset);

        System.out.println("Decoded metadata: " + metadata);
        System.out.println("Request body: " + body);

        String verdict = pythonAnalysisService.analyze(body, metadata);
        if ("Threat".equalsIgnoreCase(verdict)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }
}
// package com.example.uba_research.security;

// import jakarta.servlet.http.HttpServletRequest;
// import jakarta.servlet.http.HttpServletResponse;
// import org.springframework.stereotype.Component;
// import org.springframework.web.servlet.HandlerInterceptor;

// import com.example.uba_research.service.PythonAnalysisService;

// import java.nio.charset.StandardCharsets;

// @Component
// public class AnomalyDetectionInterceptor implements HandlerInterceptor {

//     private final PythonAnalysisService pythonAnalysisService;

//     public AnomalyDetectionInterceptor(PythonAnalysisService pythonAnalysisService) {
//         this.pythonAnalysisService = pythonAnalysisService;
//     }

//     @Override
//     public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//         String metadata = (String) request.getAttribute("DECODED_METADATA");
//         byte[] bodyBytes = request.getInputStream().readAllBytes();
//         String charset = request.getCharacterEncoding() != null ? request.getCharacterEncoding() : StandardCharsets.UTF_8.name();
//         String body = new String(bodyBytes, charset);

//         System.out.println("Decoded metadata: " + metadata);
//         System.out.println("Request body: " + body);

//         String verdict = pythonAnalysisService.analyze(body, metadata);
//         if ("Threat".equalsIgnoreCase(verdict)) {
//             response.setStatus(HttpServletResponse.SC_FORBIDDEN);
//             return false;
//         }
//         return true;
//     }
// }