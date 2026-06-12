package com.forensic.audit.controller;

import com.forensic.audit.analysis.VAEAnalysis;
import com.forensic.audit.commons.Metadata;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class TestVAEController {

    private final VAEAnalysis vaeAnalysis;

    public TestVAEController(VAEAnalysis vaeAnalysis) {
        this.vaeAnalysis = vaeAnalysis;
    }

    @PostMapping("/vae-test")
    public Map<String, Object> testVae(@RequestBody Metadata<?> metadata) {
        VAEAnalysis.AnomalyResult result = vaeAnalysis.analyze(metadata);
        return Map.of(
                "reconstructionError", result.reconstructionError(),
                "normalProbability",   result.normalProbability(),
                "accepted",            result.accepted()
        );
    }
}
