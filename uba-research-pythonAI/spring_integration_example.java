// Example Spring Boot PreHandle implementation
// Add this to your Spring Boot project

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@Component
public class UserBehaviorInterceptor implements HandlerInterceptor {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String pythonScriptPath = "C:\\path\\to\\your\\anomaly_detector.py";
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
        // Only check specific endpoints (like registration, login)
        String requestURI = request.getRequestURI();
        if (!requestURI.contains("/register") && !requestURI.contains("/login")) {
            return true; // Skip check for other endpoints
        }
        
        try {
            // Get request body (user behavior data)
            String requestBody = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            
            if (requestBody.isEmpty()) {
                return true; // No data to analyze
            }
            
            // Parse JSON to validate it contains behavior data
            Map<String, Object> payload = objectMapper.readValue(requestBody, Map.class);
            
            // Check if payload contains behavior tracking data
            if (!payload.containsKey("keysPressed") || !payload.containsKey("mouseClicks")) {
                return true; // No behavior data, allow request
            }
            
            // Call Python anomaly detector
            boolean isMalicious = callAnomalyDetector(requestBody);
            
            if (isMalicious) {
                // Log suspicious activity
                System.out.println("Suspicious user behavior detected from IP: " + request.getRemoteAddr());
                
                // Block the request
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"Suspicious activity detected\"}");
                return false;
            }
            
            return true; // Allow normal requests
            
        } catch (Exception e) {
            // Log error but don't block request on system failure
            System.err.println("Error in behavior analysis: " + e.getMessage());
            return true;
        }
    }
    
    private boolean callAnomalyDetector(String jsonPayload) {
        try {
            // Escape JSON for command line
            String escapedJson = jsonPayload.replace("\"", "\\\"");
            
            // Build command
            String[] command = {
                "python",
                pythonScriptPath,
                "\"" + escapedJson + "\""
            };
            
            // Execute Python script
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            
            // Wait for process to complete
            int exitCode = process.waitFor();
            
            if (exitCode == 0 && result != null) {
                return "true".equals(result.trim());
            }
            
            return false; // Default to safe
            
        } catch (Exception e) {
            System.err.println("Error calling Python detector: " + e.getMessage());
            return false; // Default to safe on error
        }
    }
}

// Configuration class to register the interceptor
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private UserBehaviorInterceptor userBehaviorInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userBehaviorInterceptor)
                .addPathPatterns("/api/**") // Apply to API endpoints
                .excludePathPatterns("/api/health", "/api/status"); // Exclude health checks
    }
}