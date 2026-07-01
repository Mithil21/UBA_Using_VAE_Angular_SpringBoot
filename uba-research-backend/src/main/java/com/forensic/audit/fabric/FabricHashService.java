package com.forensic.audit.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forensic.audit.uba.UbaAccepted;
import com.forensic.audit.uba.UbaRejected;
import com.forensic.audit.uba.UbaReview;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * FabricHashService — computes SHA-256 hashes of DB records
 * for commitment to the Fabric ledger.
 *
 * The hash covers all fields that matter for forensic integrity.
 * Any change to the DB row will produce a different hash,
 * which will mismatch the original hash on the Fabric ledger.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FabricHashService {

    private final ObjectMapper objectMapper;

    // ── Hash computation ───────────────────────────────────────────────────

    public String hashAccepted(UbaAccepted record) {
        String content = String.join("|",
                record.getRecordId(),
                record.getEmail(),
                record.getDecision(),
                record.getCreatedAt().toString(),
                String.valueOf(record.getTelemetry().getNormalProbability()),
                String.valueOf(record.getTelemetry().getReconstructionError())
        );
        return sha256(content);
    }

    public String hashReview(UbaReview record) {
        String content = String.join("|",
                record.getRecordId(),
                record.getEmail(),
                record.getDecision(),
                record.getCreatedAt().toString(),
                String.valueOf(record.getTelemetry().getNormalProbability()),
                String.valueOf(record.getTelemetry().getReconstructionError())
        );
        return sha256(content);
    }

    public String hashRejected(UbaRejected record) {
        String content = String.join("|",
                record.getRecordId(),
                record.getEmail(),
                record.getDecision(),
                record.getCreatedAt().toString(),
                String.valueOf(record.getTelemetry().getNormalProbability()),
                String.valueOf(record.getTelemetry().getReconstructionError()),
                record.getRejectionReason() != null ? record.getRejectionReason() : ""
        );
        return sha256(content);
    }

    // ── SHA-256 ────────────────────────────────────────────────────────────

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}