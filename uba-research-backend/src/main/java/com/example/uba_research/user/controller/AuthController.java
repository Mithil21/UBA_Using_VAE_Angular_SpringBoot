package com.example.uba_research.user.controller;

import com.example.uba_research.common.Response;
import com.example.uba_research.user.dto.LoginDto;
import com.example.uba_research.user.dto.RegisterDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    @PostMapping("/register")
    public ResponseEntity<Response<String>> register(@RequestBody RegisterDto registerDto) {
        Response<String> response = new Response<>("User registered successfully", "Success", true);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Response<String>> login(@RequestBody LoginDto loginDto) {
        Response<String> response = new Response<>("Login successful", "Success", true);
        return ResponseEntity.ok(response);
    }
}