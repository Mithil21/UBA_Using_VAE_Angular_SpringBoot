package com.example.uba_research.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
@CrossOrigin(origins = "http://localhost:4200")
public class SecurityController {

    @Autowired
    private SecurityAnalysisService securityService;

    @PostMapping("/insider-activity")
    public ResponseEntity<Map<String, Object>> trackInsiderActivity(@RequestBody Map<String, Object> activity) {
        Map<String, Object> result = securityService.analyzeInsiderThreat(activity);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/privilege-usage")
    public ResponseEntity<Map<String, Object>> trackPrivilegeUsage(@RequestBody Map<String, Object> privilegeEvent) {
        Map<String, Object> result = securityService.analyzePrivilegeEscalation(privilegeEvent);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/data-access")
    public ResponseEntity<Map<String, Object>> trackDataAccess(@RequestBody Map<String, Object> accessEvent) {
        Map<String, Object> result = securityService.analyzeComplianceViolation(accessEvent);
        return ResponseEntity.ok(result);
    }
}