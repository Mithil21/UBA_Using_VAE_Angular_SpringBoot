package com.example.uba_research.security.exception;

import com.example.uba_research.common.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaliciousInputException.class)
    public ResponseEntity<Response<Map<String, Object>>> handleMaliciousInput(MaliciousInputException ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("anomalyScore", ex.getAnomalyScore());
        errorDetails.put("timestamp", ex.getTimestamp());
        errorDetails.put("reason", "Input detected as potentially malicious");

        Response<Map<String, Object>> response = new Response<>(
            errorDetails,
            "Request blocked due to suspicious activity",
            false
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
}