package com.forensic.audit.controller;

import com.forensic.audit.commons.Payload;
import com.forensic.audit.email.EmailService;
import com.forensic.audit.exception.DuplicateEmailException;
import com.forensic.audit.kafka.VAERequestProducer;
import com.forensic.audit.user.User;
import com.forensic.audit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final VAERequestProducer vaeRequestProducer;
    private final UserRepository userRepository;
    private final EmailService emailService;

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Payload<User> payload) {
        try {
            String requestId = vaeRequestProducer.publish(
                    "REGISTER",
                    payload.getPayload(),
                    payload.getMetadata()
            );

            return ResponseEntity.accepted().body(Map.of(
                    "requestId", requestId,
                    "message",   "Registration request received. You will receive an email shortly.",
                    "state",     "RECEIVED"
            ));

        } catch (DuplicateEmailException e) {
            return ResponseEntity.status(409).body(Map.of(
                    "error",   e.getMessage(),
                    "state",   "REJECTED",
                    "message", "An account with this email already exists."
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error",   e.getMessage(),
                    "state",   "REJECTED",
                    "message", "Invalid registration details."
            ));

        } catch (Exception e) {
            log.error("[Auth] Registration failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error",   "Registration could not be processed at this time.",
                    "state",   "ERROR",
                    "message", "Please try again later."
            ));
        }
    }


    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Payload<User> payload) {
        try {
            String email    = payload.getPayload().getEmail();
            String password = payload.getPayload().getPassword();

            if (email == null || email.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error",   "Email address is required.",
                        "message", "Invalid login details."
                ));
            }

            // Check email exists
            if (!userRepository.existsByEmail(email)) {
                return ResponseEntity.status(401).body(Map.of(
                        "error",   "Invalid email or password.",
                        "message", "Invalid login details."
                ));
            }

            // Check password matches
            User user = userRepository.findByEmail(email)
                    .orElseThrow();
            if (!password.equals(user.getPassword())) {
                return ResponseEntity.status(401).body(Map.of(
                        "error",   "Invalid email or password.",
                        "message", "Invalid login details."
                ));
            }

            // Login successful — send email with dashboard link
            String username = email.split("@")[0];
            emailService.sendWelcomeEmail(email, username, "LOGIN");

            return ResponseEntity.ok().body(Map.of(
                    "message", "Login successful. Check your email.",
                    "state",   "ACCEPTED",
                    "email",   email
            ));

        } catch (Exception e) {
            log.error("[Auth] Login failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error",   "Login could not be processed at this time.",
                    "message", "Please try again later."
            ));
        }
    }
}