package com.forensic.audit.controller;

import com.forensic.audit.commons.Payload;
import com.forensic.audit.kafka.VAERequestProducer;
import com.forensic.audit.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final VAERequestProducer vaeRequestProducer;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Payload<User> payload) {
        String requestId = vaeRequestProducer.publish(
                "REGISTER",
                payload.getPayload(),
                payload.getMetadata()
        );

        return ResponseEntity.accepted().body(Map.of(
                "requestId", requestId,
                "message",   "Registration request received. Email will be sent accordingly" + requestId,
                "state",     "RECEIVED"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Payload<User> payload) {
        String requestId = vaeRequestProducer.publish(
                "LOGIN",
                payload.getPayload(),
                payload.getMetadata()
        );

        return ResponseEntity.accepted().body(Map.of(
                "requestId", requestId,
                "message",   "Login request received — please poll /api/auth/status/" + requestId,
                "state",     "RECEIVED"
        ));
    }
}