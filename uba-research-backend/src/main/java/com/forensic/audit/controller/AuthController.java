package com.forensic.audit.controller;

import com.forensic.audit.analysis.VAEAnalysis;
import com.forensic.audit.commons.Payload;
import com.forensic.audit.user.User;
import com.forensic.audit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {


//    @Autowired
    private final UserRepository userRepository;
    private final VAEAnalysis vaeAnalysis;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody Payload<User> payload) {

        VAEAnalysis.AnomalyResult analysis = vaeAnalysis.analyze(payload.getMetadata());
        System.out.printf("[VAE] error=%.6f probability=%.4f accepted=%b%n",
                analysis.reconstructionError(), analysis.normalProbability(), analysis.accepted());
        if (!analysis.accepted()) {
            return ResponseEntity.status(403).body("Behavioural analysis rejected this request");
        }
        if (userRepository.existsByEmail(payload.getPayload().getEmail())) {
            return ResponseEntity.badRequest().body("Email already exists");
        }
        userRepository.save(payload.getPayload());
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody User user) {
        User existingUser = userRepository.findByEmail(user.getEmail())
                .orElse(null);
        if (existingUser == null || !existingUser.getPassword().equals(user.getPassword())) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
        return ResponseEntity.ok("Login successful");
    }
}