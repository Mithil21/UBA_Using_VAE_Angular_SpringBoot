package com.forensic.audit.kafka;

import com.forensic.audit.commons.Metadata;
import com.forensic.audit.exception.DuplicateEmailException;
import com.forensic.audit.user.User;
import com.forensic.audit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VAERequestProducer{

    private final KafkaTemplate<String, VAEAnalysisMessage> kafkaTemplate;
    private final AnalysisRequestRepository requestRepository;
    private final UserRepository userRepository;

    /**
     * Publishes a VAE analysis request to the Kafka topic.
     * Saves an RECEIVED state record first so the client can poll immediately.
     * Returns the requestId for the client to poll against.
     */
    public String publish(String requestType, User user, Metadata<User> metadata) {
        String requestId = java.util.UUID.randomUUID().toString();

        // ── Email validation ──────────────────────────────────────────────
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email address is required");
        }
        // Basic email format check
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email address format: " + email);
        }

        // ── Duplicate email check (REGISTER only) ─────────────────────────
        if ("REGISTER".equals(requestType)) {
            if (userRepository.existsByEmail(email)) {
                log.warn("[Kafka] Duplicate email rejected at producer level: {}", email);
                throw new DuplicateEmailException("An account with this email already exists");
            }
        }

        // ── Persist initial state — RECEIVED ──────────────────────────────
        AnalysisRequest request = AnalysisRequest.received(requestId);
        requestRepository.save(request);
        log.info("[Kafka] Saved RECEIVED state for requestId={}", requestId);

        // ── Build and publish message ──────────────────────────────────────
        VAEAnalysisMessage message = new VAEAnalysisMessage(requestId, requestType, user, metadata);

        kafkaTemplate.send(KafkaConfig.TOPIC_NAME, requestId, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka] Failed to publish requestId={} — {}", requestId, ex.getMessage());
                        request.markFailed("Failed to publish to Kafka: " + ex.getMessage());
                        requestRepository.save(request);
                    } else {
                        log.info("[Kafka] Published requestId={} to topic={} partition={}",
                                requestId,
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition());
                    }
                });

        return requestId;
    }
}