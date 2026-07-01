package com.forensic.audit.kafka;

import com.forensic.audit.analysis.VAEAnalysis;
import com.forensic.audit.analysis.VAEAnalysis.Decision;
import com.forensic.audit.email.EmailService;
import com.forensic.audit.uba.*;
import com.forensic.audit.user.User;
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
    private final UbaAcceptedRepository     acceptedRepository;
    private final UbaReviewRepository      reviewRepository;
    private final UbaRejectedRepository     rejectedRepository;
    private final EmailService              emailService;
    private final KafkaTemplate<String, VAEAnalysisMessage> kafkaTemplate;

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
        String             password  = message.getUser().getPassword();
        String             firstName = message.getUser().getFirstName();
        String             lastName  = message.getUser().getLastName();

        log.info("[Consumer] Received requestId={} type={} email={}",
                requestId, message.getRequestType(), email);

        // Update Kafka state tracking record
        AnalysisRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null) {
            log.warn("[Consumer] No state record for requestId={} — skipping", requestId);
            ack.acknowledge();
            return;
        }

        request.markProcessing();
        requestRepository.save(request);

        try {
            // ── VAE inference ─────────────────────────────────────────────
            VAEAnalysis.AnomalyResult result = vaeAnalysis.analyze(message.getMetadata());
            Decision decision = result.decision();

            log.info("[Consumer] VAE decision={} mse={} prob={} requestId={}",
                    decision, result.reconstructionError(),
                    result.normalProbability(), requestId);

            // ── Build full telemetry snapshot ─────────────────────────────
            // Same snapshot goes into whichever table we write to
            UbaTelemetrySnapshot snapshot = vaeAnalysis.buildSnapshot(
                    message.getMetadata(), result);

            // ── Route to correct table ────────────────────────────────────
            if (decision == Decision.ACCEPTED) {
                handleAccepted(request, message, email, password,
                        firstName, lastName, snapshot, result);

            } else if (decision == Decision.REVIEW) {
                handleReview(request, email, password,
                        firstName, lastName, snapshot, result);

            } else {
                handleRejected(request, email, password, snapshot, result,
                        "VAE_SCORE_LOW");
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Consumer] Error on requestId={} — {}",
                    requestId, e.getMessage(), e);
            handleFailure(request, message, ack, email, e.getMessage());
        }
    }

    // ── ACCEPTED ─────────────────────────────────────────────────────────

    private void handleAccepted(AnalysisRequest request,
                                VAEAnalysisMessage message,
                                String email, String password,
                                String firstName, String lastName,
                                UbaTelemetrySnapshot snapshot,
                                VAEAnalysis.AnomalyResult result) {

        if ("REGISTER".equals(message.getRequestType())) {

            if (userRepository.existsByEmail(email)) {
                // Duplicate email — save to rejected with reason
                UbaRejected rejected = new UbaRejected(
                        email, password, "DUPLICATE_EMAIL", snapshot);
                rejectedRepository.save(rejected);
                log.info("[Consumer] DUPLICATE_EMAIL — saved to uba_rejected email={}", email);

                request.markRejected(result.reconstructionError(), result.normalProbability());
                request.setMessage("Email already exists");
                requestRepository.save(request);
                emailService.sendRejectionEmail(email);
                return;
            }

            // Save user to users table
            User user = new User(email, password);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            userRepository.save(user);

            // Save full record to uba_accepted
            UbaAccepted accepted = new UbaAccepted(
                    email, password, firstName, lastName, snapshot);
            acceptedRepository.save(accepted);

            request.markAccepted(result.reconstructionError(), result.normalProbability());
            requestRepository.save(request);

            String username = email.split("@")[0];
            emailService.sendWelcomeEmail(email, username);
            log.info("[Consumer] ACCEPTED — user saved, welcome email sent email={}", email);

        } else {
            // LOGIN — VAE accepted, credential check is separate concern
            // Save to uba_accepted as a successful login behavioural record
            UbaAccepted accepted = new UbaAccepted(
                    email, password, firstName, lastName, snapshot);
            acceptedRepository.save(accepted);

            request.markAccepted(result.reconstructionError(), result.normalProbability());
            requestRepository.save(request);
            log.info("[Consumer] LOGIN ACCEPTED — saved to uba_accepted email={}", email);
        }
    }

    // ── REVIEW ───────────────────────────────────────────────────────────

    private void handleReview(AnalysisRequest request,
                              String email, String password,
                              String firstName, String lastName,
                              UbaTelemetrySnapshot snapshot,
                              VAEAnalysis.AnomalyResult result) {

        UbaReview review = new UbaReview(
                email, password, firstName, lastName, snapshot);
        reviewRepository.save(review);

        request.markRejected(result.reconstructionError(), result.normalProbability());
        request.setMessage("Registration under review");
        requestRepository.save(request);

        // On-hold email — different from rejection and system error
        emailService.sendOnHoldEmail(email);
        log.info("[Consumer] REVIEW — saved to uba_review, on-hold email sent email={}", email);
    }

    // ── REJECTED ─────────────────────────────────────────────────────────

    private void handleRejected(AnalysisRequest request,
                                String email, String password,
                                UbaTelemetrySnapshot snapshot,
                                VAEAnalysis.AnomalyResult result,
                                String reason) {

        UbaRejected rejected = new UbaRejected(email, password, reason, snapshot);
        rejectedRepository.save(rejected);

        request.markRejected(result.reconstructionError(), result.normalProbability());
        requestRepository.save(request);

        // Deliberately vague — never tell attacker why they were rejected
        emailService.sendRejectionEmail(email);
        log.info("[Consumer] REJECTED ({}) — saved to uba_rejected email={}", reason, email);
    }

    // ── FAILURE / RETRY / DEAD LETTER ────────────────────────────────────

    private void handleFailure(AnalysisRequest request,
                               VAEAnalysisMessage message,
                               Acknowledgment ack,
                               String email,
                               String errorMessage) {
        request.incrementRetry();

        if (request.getRetryCount() >= KafkaConfig.MAX_RETRIES) {
            log.error("[Consumer] Dead letter after {} retries requestId={}",
                    KafkaConfig.MAX_RETRIES, request.getRequestId());

            request.markDeadLetter();
            requestRepository.save(request);

            kafkaTemplate.send(KafkaConfig.VAE_DEAD_LETTER_TOPIC,
                    request.getRequestId(), message);

            // System error — not bot detection — user is not at fault
            emailService.sendOnHoldEmail(email);
            log.info("[Consumer] Dead letter email sent email={}", email);

            ack.acknowledge();
        } else {
            log.warn("[Consumer] Retry {}/{} requestId={}",
                    request.getRetryCount(), KafkaConfig.MAX_RETRIES,
                    request.getRequestId());
            request.markFailed(errorMessage);
            requestRepository.save(request);
            // No ack — Kafka redelivers
        }
    }
}