package com.forensic.audit.kafka;

import com.forensic.audit.analysis.VAEAnalysis;
import com.forensic.audit.email.EmailService;
import com.forensic.audit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VAERequestConsumer {

    private final VAEAnalysis               vaeAnalysis;
    private final UserRepository            userRepository;
    private final AnalysisRequestRepository requestRepository;
    private final KafkaTemplate<String, VAEAnalysisMessage> kafkaTemplate;
    private final EmailService emailService;

    @KafkaListener(
            topics           = KafkaConfig.TOPIC_NAME,
            groupId          = "uba-vae-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, VAEAnalysisMessage> record,
                        Acknowledgment ack) {

        VAEAnalysisMessage message   = record.value();
        String             requestId = message.getRequestId();
        String             email     = message.getUser().getEmail();

        log.info("[Consumer] Received requestId={} type={}", requestId, message.getRequestType());

        AnalysisRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null) {
            log.warn("[Consumer] No state record for requestId={} — skipping", requestId);
            ack.acknowledge();
            return;
        }

        // Transition to PROCESSING
        request.markProcessing();
        requestRepository.save(request);

        try {
            // ── VAE inference ──────────────────────────────────────────────
            VAEAnalysis.AnomalyResult result = vaeAnalysis.analyze(message.getMetadata());

            log.info("[Consumer] VAE — requestId={} mse={} prob={} accepted={}",
                    requestId, result.reconstructionError(),
                    result.normalProbability(), result.accepted());

            if (!result.accepted()) {
                // ── Rejected — bot detected ────────────────────────────────
                request.markRejected(result.reconstructionError(), result.normalProbability());
                requestRepository.save(request);

                // Deliberately vague — don't tell attacker why they were blocked
                emailService.sendRejectionEmail(email);
                log.info("[Consumer] REJECTED + rejection email sent — requestId={}", requestId);

            } else {
                // ── Accepted ───────────────────────────────────────────────
                if ("REGISTER".equals(message.getRequestType())) {

                    if (userRepository.existsByEmail(email)) {
                        // Duplicate email — treat as soft rejection
                        request.markRejected(
                                result.reconstructionError(),
                                result.normalProbability()
                        );
                        request.setMessage("Email already exists");
                        requestRepository.save(request);
                        emailService.sendRejectionEmail(email);
                        log.info("[Consumer] DUPLICATE EMAIL — requestId={}", requestId);

                    } else {
                        userRepository.save(message.getUser());
                        request.markAccepted(
                                result.reconstructionError(),
                                result.normalProbability()
                        );
                        requestRepository.save(request);

                        // Welcome email — use email as username fallback
                        String username = message.getUser().getEmail().split("@")[0];
                        emailService.sendWelcomeEmail(email, username);
                        log.info("[Consumer] ACCEPTED + welcome email sent — requestId={}", requestId);
                    }

                } else {
                    // LOGIN — just mark accepted, no email needed
                    request.markAccepted(
                            result.reconstructionError(),
                            result.normalProbability()
                    );
                    requestRepository.save(request);
                    log.info("[Consumer] LOGIN ACCEPTED — requestId={}", requestId);
                }
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Consumer] Error on requestId={} — {}", requestId, e.getMessage(), e);
            handleFailure(request, message, ack, email, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Retry + dead letter
    // -----------------------------------------------------------------------

    private void handleFailure(AnalysisRequest request,
                               VAEAnalysisMessage message,
                               Acknowledgment ack,
                               String email,
                               String errorMessage) {
        request.incrementRetry();

        if (request.getRetryCount() >= KafkaConfig.MAX_RETRIES) {
            log.error("[Consumer] Dead letter — requestId={} after {} retries",
                    request.getRequestId(), KafkaConfig.MAX_RETRIES);

            request.markDeadLetter();
            requestRepository.save(request);

            // Send to dead letter topic for manual inspection
            kafkaTemplate.send(KafkaConfig.VAE_DEAD_LETTER_TOPIC,
                    request.getRequestId(), message);

            // Tell user registration is on hold — not their fault
            emailService.sendOnHoldEmail(email);
            log.info("[Consumer] On-hold email sent for requestId={}", request.getRequestId());

            ack.acknowledge();

        } else {
            log.warn("[Consumer] Retry {}/{} — requestId={}",
                    request.getRetryCount(), KafkaConfig.MAX_RETRIES, request.getRequestId());

            request.markFailed(errorMessage);
            requestRepository.save(request);
            // No ack — Kafka redelivers
        }
    }
}
