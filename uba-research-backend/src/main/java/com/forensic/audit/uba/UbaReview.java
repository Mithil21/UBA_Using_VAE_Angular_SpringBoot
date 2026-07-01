package com.forensic.audit.uba;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores borderline cases where 0.40 <= probability <= 0.65.
 *
 * Purpose:
 *   1. Human-in-the-loop labelling queue
 *      A reviewer labels each record as LEGITIMATE or BOT
 *   2. Labelled records feed back into VAE retraining
 *   3. Enables empirical threshold calibration over time
 *
 * Email sent: "Registration on hold — we are reviewing your request"
 */
@Entity
@Table(name = "uba_review")
@Data
@NoArgsConstructor
public class UbaReview {

    @Id
    @Column(nullable = false, updatable = false)
    private String recordId = UUID.randomUUID().toString();

    // ── User details ──────────────────────────────────────────────────────
    @Column(nullable = false)
    private String email;

    private String password;
    private String firstName;
    private String lastName;

    // ── Decision ──────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String decision = "REVIEW";

    // Human reviewer label — populated after manual review
    // Values: null (pending), "LEGITIMATE", "BOT"
    @Column
    private String reviewLabel;

    @Column
    private String reviewNotes;

    @Column
    private LocalDateTime reviewedAt;

    @Column
    private String reviewedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Full telemetry snapshot ───────────────────────────────────────────
    @Embedded
    private UbaTelemetrySnapshot telemetry;

    // ── Fabric audit ──────────────────────────────────────────────────────
    @Column(length = 64)
    private String fabricHash;

    @Column
    private LocalDateTime fabricCommittedAt;

    // ── Constructor ───────────────────────────────────────────────────────
    public UbaReview(String email, String password,
                     String firstName, String lastName,
                     UbaTelemetrySnapshot telemetry) {
        this.email     = email;
        this.password  = password;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.telemetry = telemetry;
    }
}