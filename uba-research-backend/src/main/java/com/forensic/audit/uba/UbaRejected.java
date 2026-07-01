package com.forensic.audit.uba;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores every VAE-rejected request (probability < 0.40).
 *
 * Purpose:
 *   1. Forensic audit trail — immutable evidence of attack attempts
 *   2. Fabric hash ensures admin cannot delete evidence
 *   3. Bot MSE distribution informs threshold calibration
 *   4. Attack pattern analysis (IP, timing, feature signatures)
 *
 * Email sent: deliberately vague rejection — no reason given
 * (avoids giving attacker feedback to tune their bot)
 */
@Entity
@Table(name = "uba_rejected")
@Data
@NoArgsConstructor
public class UbaRejected {

    @Id
    @Column(nullable = false, updatable = false)
    private String recordId = UUID.randomUUID().toString();

    // ── Attempted user details ────────────────────────────────────────────
    // Stored for forensic analysis — which emails are being targeted
    @Column(nullable = false)
    private String email;

    // Password attempt — stored for forensic analysis
    // Helps identify credential stuffing lists being used
    @Column
    private String password;

    // ── Decision ──────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String decision = "REJECTED";

    // Reason codes for internal analysis (not exposed to user)
    // e.g. "VAE_SCORE_LOW", "DUPLICATE_EMAIL", "SYSTEM_ERROR"
    @Column
    private String rejectionReason;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Full telemetry snapshot ───────────────────────────────────────────
    @Embedded
    private UbaTelemetrySnapshot telemetry;

    // ── Fabric audit ──────────────────────────────────────────────────────
    // Critical — rejected records on Fabric cannot be deleted by admin
    // This is the non-repudiation guarantee
    @Column(length = 64)
    private String fabricHash;

    @Column
    private LocalDateTime fabricCommittedAt;

    // ── Constructor ───────────────────────────────────────────────────────
    public UbaRejected(String email, String password,
                       String rejectionReason,
                       UbaTelemetrySnapshot telemetry) {
        this.email           = email;
        this.password        = password;
        this.rejectionReason = rejectionReason;
        this.telemetry       = telemetry;
    }
}