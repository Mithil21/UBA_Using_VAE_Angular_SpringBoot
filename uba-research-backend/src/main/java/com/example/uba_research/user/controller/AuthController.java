package com.example.uba_research.user.controller;

import com.example.uba_research.common.Request;
import com.example.uba_research.common.Response;
import com.example.uba_research.user.dto.LoginDto;
import com.example.uba_research.user.dto.RegisterDto;
import com.example.uba_research.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    @Autowired
    private UserService service;

    @PostMapping("/register")
    @CrossOrigin(origins = "http://localhost:4200",allowedHeaders = "*",allowCredentials = "true")
    public ResponseEntity<Response<String>> register(@RequestBody Request<RegisterDto> registerDto) {
        boolean flag = service.checkAuthenticity(registerDto.getPayload(), registerDto.getMetadata());
        Response<String> response = new Response<>("User registered successfully", "Success", true);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    @CrossOrigin(origins = "http://localhost:4200",allowedHeaders = "*",allowCredentials = "true")
    public ResponseEntity<Object> login(@RequestBody Request<LoginDto> request) {
        LoginDto loginDto = request.getPayload();
        
        // Simulate user validation and role assignment
        String role = loginDto.getUsername().contains("admin") ? "admin" : "user";
        String userId = "user_" + System.currentTimeMillis();
        
        Object responseData = java.util.Map.of(
            "userId", userId,
            "username", loginDto.getUsername(),
            "role", role,
            "message", "Login successful"
        );
        
        Response<Object> response = new Response<>("Login successful", (String) responseData, true);
        return ResponseEntity.ok(response);
    }
}