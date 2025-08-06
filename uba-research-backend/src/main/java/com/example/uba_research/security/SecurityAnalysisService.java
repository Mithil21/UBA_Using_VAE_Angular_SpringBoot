package com.example.uba_research.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.uba_research.service.PythonAnalysisService;
import java.util.*;

@Service
public class SecurityAnalysisService {

    @Autowired
    private PythonAnalysisService pythonService;

    // 7. Insider Threats Analysis
    public Map<String, Object> analyzeInsiderThreat(Map<String, Object> activity) {
        Map<String, Object> result = new HashMap<>();
        
        boolean isAfterHours = (Boolean) activity.get("isAfterHours");
        int timeOfDay = (Integer) activity.get("timeOfDay");
        String role = (String) activity.get("role");
        long sessionDuration = (Long) activity.get("sessionDuration");
        
        List<String> threats = new ArrayList<>();
        
        if (isAfterHours && !role.equals("admin")) {
            threats.add("AFTER_HOURS_ACCESS");
        }
        
        if (sessionDuration > 28800000) { // 8 hours
            threats.add("EXCESSIVE_SESSION_DURATION");
        }
        
        if (timeOfDay >= 0 && timeOfDay <= 5) {
            threats.add("UNUSUAL_TIME_ACCESS");
        }
        
        // AI-enhanced detection
        try {
            Map<String, Object> aiResult = callEnhancedDetector("insider", activity);
            if ((Boolean) aiResult.getOrDefault("is_anomaly", false)) {
                threats.add("AI_DETECTED_ANOMALY");
            }
            result.put("aiConfidence", aiResult.get("confidence"));
        } catch (Exception e) {
            result.put("aiError", e.getMessage());
        }
        
        result.put("threats", threats);
        result.put("riskLevel", threats.size() > 1 ? "HIGH" : threats.size() == 1 ? "MEDIUM" : "LOW");
        result.put("timestamp", new Date());
        
        return result;
    }

    // 8. Privilege Escalation Analysis
    public Map<String, Object> analyzePrivilegeEscalation(Map<String, Object> privilegeEvent) {
        Map<String, Object> result = new HashMap<>();
        
        String role = (String) privilegeEvent.get("role");
        String expectedScope = (String) privilegeEvent.get("expectedScope");
        String actualScope = (String) privilegeEvent.get("actualScope");
        String permission = (String) privilegeEvent.get("permission");
        
        List<String> violations = new ArrayList<>();
        
        if (!expectedScope.equals(actualScope)) {
            violations.add("SCOPE_VIOLATION");
        }
        
        if (permission.contains("admin") && !role.equals("admin")) {
            violations.add("UNAUTHORIZED_ADMIN_ACCESS");
        }
        
        if (permission.contains("delete") && role.equals("user")) {
            violations.add("UNAUTHORIZED_DELETE_PERMISSION");
        }
        
        // AI-enhanced detection
        try {
            Map<String, Object> aiResult = callEnhancedDetector("privilege", privilegeEvent);
            if ((Boolean) aiResult.getOrDefault("is_anomaly", false)) {
                violations.add("AI_DETECTED_PRIVILEGE_ANOMALY");
            }
            result.put("aiConfidence", aiResult.get("confidence"));
        } catch (Exception e) {
            result.put("aiError", e.getMessage());
        }
        
        result.put("violations", violations);
        result.put("blocked", violations.size() > 0);
        result.put("riskLevel", violations.size() > 1 ? "CRITICAL" : violations.size() == 1 ? "HIGH" : "LOW");
        
        return result;
    }

    // 9. Malware Detection (enhanced with AI)
    public Map<String, Object> analyzeMalwareIndicators(Map<String, Object> indicators) {
        Map<String, Object> result = new HashMap<>();
        
        @SuppressWarnings("unchecked")
        List<String> suspiciousExtensions = (List<String>) indicators.get("suspiciousExtensions");
        boolean automatedTraffic = (Boolean) indicators.get("automatedTraffic");
        boolean browserIntegrity = (Boolean) indicators.get("browserIntegrity");
        
        List<String> malwareFlags = new ArrayList<>();
        
        if (suspiciousExtensions != null && !suspiciousExtensions.isEmpty()) {
            malwareFlags.add("SUSPICIOUS_BROWSER_EXTENSIONS");
        }
        
        if (automatedTraffic) {
            malwareFlags.add("AUTOMATED_TRAFFIC_DETECTED");
        }
        
        if (!browserIntegrity) {
            malwareFlags.add("BROWSER_INTEGRITY_COMPROMISED");
        }
        
        // Use AI for advanced detection
        try {
            boolean aiDetection = pythonService.detectMalware(indicators);
            if (aiDetection) {
                malwareFlags.add("AI_MALWARE_DETECTION");
            }
        } catch (Exception e) {
            malwareFlags.add("AI_ANALYSIS_FAILED");
        }
        
        result.put("malwareFlags", malwareFlags);
        result.put("infected", malwareFlags.size() > 1);
        result.put("riskLevel", malwareFlags.size() > 2 ? "CRITICAL" : malwareFlags.size() > 0 ? "HIGH" : "LOW");
        
        return result;
    }

    // 10. Compliance Violations Analysis
    public Map<String, Object> analyzeComplianceViolation(Map<String, Object> accessEvent) {
        Map<String, Object> result = new HashMap<>();
        
        String dataType = (String) accessEvent.get("dataType");
        @SuppressWarnings("unchecked")
        List<String> complianceFlags = (List<String>) accessEvent.get("complianceFlags");
        
        List<String> violations = new ArrayList<>();
        
        if (complianceFlags != null) {
            violations.addAll(complianceFlags);
        }
        
        // GDPR checks
        if (dataType.contains("personal") && !hasConsent(accessEvent)) {
            violations.add("GDPR_NO_CONSENT");
        }
        
        // HIPAA checks
        if (dataType.contains("medical") && !isAuthorizedMedical(accessEvent)) {
            violations.add("HIPAA_UNAUTHORIZED_ACCESS");
        }
        
        // AI-enhanced compliance detection
        try {
            Map<String, Object> aiResult = callEnhancedDetector("compliance", accessEvent);
            if ((Boolean) aiResult.getOrDefault("is_anomaly", false)) {
                violations.add("AI_DETECTED_COMPLIANCE_ANOMALY");
            }
            result.put("aiConfidence", aiResult.get("confidence"));
        } catch (Exception e) {
            result.put("aiError", e.getMessage());
        }
        
        result.put("violations", violations);
        result.put("compliant", violations.isEmpty());
        result.put("auditRequired", violations.size() > 0);
        result.put("riskLevel", violations.size() > 1 ? "CRITICAL" : violations.size() == 1 ? "HIGH" : "LOW");
        
        return result;
    }

    private boolean hasConsent(Map<String, Object> accessEvent) {
        // Simplified consent check
        return accessEvent.containsKey("consentGiven") && (Boolean) accessEvent.get("consentGiven");
    }

    private boolean isAuthorizedMedical(Map<String, Object> accessEvent) {
        String purpose = (String) accessEvent.get("purpose");
        return purpose != null && (purpose.contains("treatment") || purpose.contains("authorized"));
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> callEnhancedDetector(String analysisType, Map<String, Object> data) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("python", "enhanced_anomaly_detector.py", analysisType, 
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data));
        pb.directory(new java.io.File("C:/Users/mithi/OneDrive/Desktop/UBA Research/uba-research-pythonAI"));
        
        Process process = pb.start();
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
        String result = reader.readLine();
        process.waitFor();
        
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(result, Map.class);
    }
}