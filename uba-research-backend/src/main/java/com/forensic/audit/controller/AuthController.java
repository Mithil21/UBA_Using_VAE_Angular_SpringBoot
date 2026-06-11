package com.forensic.audit.controller;

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


    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody Payload<User> payload) {
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