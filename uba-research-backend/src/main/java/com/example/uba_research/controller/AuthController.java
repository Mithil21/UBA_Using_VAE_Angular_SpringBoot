package com.example.uba_research.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        System.out.println("Registration request received: " + request);
        
        return ResponseEntity.ok(Map.of(
            "message", "User registered successfully",
            "status", "Success",
            "success", true
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> request) {
        System.out.println("Login request received: " + request);
        
        return ResponseEntity.ok(Map.of(
            "message", "Login successful",
            "status", "Success", 
            "success", true
        ));
    }
}