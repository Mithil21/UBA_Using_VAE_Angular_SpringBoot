package com.example.uba_research.security.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RequestBodyExtractor {

    public static String extractBody(HttpServletRequest request) throws IOException {
        if (request.getInputStream() != null) {
            return StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        }
        return "";
    }
}