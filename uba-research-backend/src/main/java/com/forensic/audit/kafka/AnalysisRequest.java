package com.forensic.audit.kafka;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_requests")
@Data
@NoArgsConstructor
public class AnalysisRequest {

    // -----------------------------------------------------------------------
    // State machine
    // -----------------------------------------------------------------------
    // RECEIVED      — request arrived at AuthController, placed on Kafka topic
    // VAE_PROCESSING — consumer picked up the message, VAE running
    // VAE_ACCEPTED  — VAE accepted, user saved to DB
    // VAE_REJECTED  — VAE rejected, request blocked
    // VAE_FAILED    — unexpected error during VAE inference
    // DEAD_LETTER   — all retries exhausted, permanently failed
    // -----------------------------------------------------------------------
    public enum State {
        RECEIVED,
        VAE_PROCESSING,
        VAE_ACCEPTED,
        VAE_REJECTED,
        VAE_FAILED,
        DEAD_LETTER
    }

    @Id
    @Column(nullable = false, updatable = false)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    // VAE result fields — populated after inference
    private Float reconstructionError;
    private Float normalProbability;

    // Human-readable outcome message returned to the client
    private String message;

    // Retry tracking
    @Column(nullable = false)
    private int retryCount = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------
    public static AnalysisRequest received(String requestId) {
        AnalysisRequest req = new AnalysisRequest();
        req.requestId  = requestId;
        req.state      = State.RECEIVED;
        req.createdAt  = LocalDateTime.now();
        req.updatedAt  = LocalDateTime.now();
        return req;
    }

    // -----------------------------------------------------------------------
    // State transitions
    // -----------------------------------------------------------------------
    public void markProcessing() {
        this.state     = State.VAE_PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAccepted(float error, float probability) {
        this.state                = State.VAE_ACCEPTED;
        this.reconstructionError  = error;
        this.normalProbability    = probability;
        this.message              = "Request accepted";
        this.updatedAt            = LocalDateTime.now();
    }

    public void markRejected(float error, float probability) {
        this.state                = State.VAE_REJECTED;
        this.reconstructionError  = error;
        this.normalProbability    = probability;
        this.message              = "Behavioural analysis rejected this request";
        this.updatedAt            = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        this.state     = State.VAE_FAILED;
        this.message   = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public void markDeadLetter() {
        this.state     = State.DEAD_LETTER;
        this.message   = "All retries exhausted — request permanently failed";
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementRetry() {
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
    }
}
