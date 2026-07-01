package com.forensic.audit.uba;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores every VAE-accepted registration.
 *
 * Purpose:
 *   1. Confirmed normal data for future VAE retraining
 *   2. Hyperledger Fabric hash target — SHA-256(this record)
 *      committed to ledger so any DB tampering is detectable
 *
 * Fabric ledger key: recordId (UUID)
 */
@Entity
@Table(name = "uba_accepted")
@Data
@NoArgsConstructor
public class UbaAccepted {

    @Id
    @Column(nullable = false, updatable = false)
    private String recordId = UUID.randomUUID().toString();

    // ── User details ──────────────────────────────────────────────────────
    @Column(nullable = false)
    private String email;

    // Password stored as received — hashing should happen in UserService
    // Stored here for audit completeness only
    @Column(nullable = false)
    private String password;

    private String firstName;
    private String lastName;

    // ── Decision ──────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String decision = "ACCEPTED";

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Full telemetry snapshot ───────────────────────────────────────────
    @Embedded
    private UbaTelemetrySnapshot telemetry;

    // ── Fabric audit ──────────────────────────────────────────────────────
    // SHA-256 hash of this record committed to Hyperledger Fabric ledger.
    // Populated after Fabric commit. Null until Fabric integration complete.
    @Column(length = 64)
    private String fabricHash;

    @Column
    private LocalDateTime fabricCommittedAt;

    // ── Constructor ───────────────────────────────────────────────────────
    public UbaAccepted(String email, String password,
                       String firstName, String lastName,
                       UbaTelemetrySnapshot telemetry) {
        this.email     = email;
        this.password  = password;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.telemetry = telemetry;
    }
}