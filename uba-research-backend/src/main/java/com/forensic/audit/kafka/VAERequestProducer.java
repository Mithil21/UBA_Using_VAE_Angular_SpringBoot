package com.forensic.audit.kafka;

import com.forensic.audit.commons.Metadata;
import com.forensic.audit.user.User;
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

    /**
     * Publishes a VAE analysis request to the Kafka topic.
     * Saves an RECEIVED state record first so the client can poll immediately.
     * Returns the requestId for the client to poll against.
     */
    public String publish(String requestType, User user, Metadata<User> metadata) {
        String requestId = java.util.UUID.randomUUID().toString();

        // Persist initial state — RECEIVED
        AnalysisRequest request = AnalysisRequest.received(requestId);
        requestRepository.save(request);
        log.info("[Kafka] Saved RECEIVED state for requestId={}", requestId);

        // Build and publish message
        VAEAnalysisMessage message = new VAEAnalysisMessage(requestId, requestType, user, metadata);

        kafkaTemplate.send(KafkaConfig.TOPIC_NAME, requestId, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka] Failed to publish requestId={} — {}", requestId, ex.getMessage());
                        // Mark as failed immediately if Kafka itself is unavailable
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