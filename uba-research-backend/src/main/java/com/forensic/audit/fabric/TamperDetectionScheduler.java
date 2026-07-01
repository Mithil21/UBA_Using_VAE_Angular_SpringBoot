package com.forensic.audit.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forensic.audit.uba.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TamperDetectionScheduler — periodic verification of DB records against Fabric ledger.
 *
 * Every hour, re-hashes all DB records that have been committed to Fabric
 * and compares against the stored hash on the ledger.
 *
 * A hash mismatch means someone modified the DB row after Fabric commit —
 * this is a tamper alert.
 *
 * Enable scheduling by adding @EnableScheduling to your main application class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TamperDetectionScheduler {

    private final FabricService      fabricService;
    private final FabricHashService  hashService;
    private final UbaAcceptedRepository acceptedRepo;
    private final UbaReviewRepository   reviewRepo;
    private final UbaRejectedRepository rejectedRepo;
    private final ObjectMapper          objectMapper;

    // ── Scheduled verification ─────────────────────────────────────────────

    /**
     * Runs every hour. Verifies all records that have been committed to Fabric.
     * Records without a fabricHash have not yet been committed — skipped.
     */
    @Scheduled(fixedRateString = "${fabric.verification.interval:3600000}")
    public void verifyAllRecords() {
        log.info("[TamperDetection] Starting verification run");

        int checked = 0, tampered = 0, missing = 0;

        // Verify accepted records
        List<UbaAccepted> accepted = acceptedRepo.findAll().stream()
                .filter(r -> r.getFabricHash() != null)
                .toList();

        for (UbaAccepted record : accepted) {
            VerificationResult result = verify(
                    record.getRecordId(),
                    record.getFabricHash(),
                    hashService.hashAccepted(record)
            );
            checked++;
            if (result == VerificationResult.TAMPERED) tampered++;
            if (result == VerificationResult.MISSING)  missing++;
        }

        // Verify review records
        List<UbaReview> reviews = reviewRepo.findAll().stream()
                .filter(r -> r.getFabricHash() != null)
                .toList();

        for (UbaReview record : reviews) {
            VerificationResult result = verify(
                    record.getRecordId(),
                    record.getFabricHash(),
                    hashService.hashReview(record)
            );
            checked++;
            if (result == VerificationResult.TAMPERED) tampered++;
            if (result == VerificationResult.MISSING)  missing++;
        }

        // Verify rejected records
        List<UbaRejected> rejected = rejectedRepo.findAll().stream()
                .filter(r -> r.getFabricHash() != null)
                .toList();

        for (UbaRejected record : rejected) {
            VerificationResult result = verify(
                    record.getRecordId(),
                    record.getFabricHash(),
                    hashService.hashRejected(record)
            );
            checked++;
            if (result == VerificationResult.TAMPERED) tampered++;
            if (result == VerificationResult.MISSING)  missing++;
        }

        if (tampered > 0) {
            log.error("[TamperDetection] ALERT — {} TAMPERED records detected out of {} checked",
                    tampered, checked);
        } else {
            log.info("[TamperDetection] Verification complete — {}/{} records intact, {} missing from ledger",
                    checked - missing, checked, missing);
        }
    }

    // ── Core verification logic ────────────────────────────────────────────

    private VerificationResult verify(String recordId,
                                      String storedFabricHash,
                                      String currentDbHash) {
        // Query Fabric ledger for the original hash
        String ledgerJson = fabricService.verifyRecord(recordId);

        if (ledgerJson == null) {
            log.warn("[TamperDetection] Record not found on ledger recordId={}", recordId);
            return VerificationResult.MISSING;
        }

        try {
            JsonNode ledgerRecord = objectMapper.readTree(ledgerJson);
            String ledgerHash = ledgerRecord.get("combinedHash").asText();

            if (!ledgerHash.equals(storedFabricHash)) {
                // The hash stored in DB fabricHash column doesn't match ledger
                // This means the fabricHash column itself was tampered with
                log.error("[TamperDetection] TAMPER ALERT — fabricHash column modified! " +
                                "recordId={} dbFabricHash={} ledgerHash={}",
                        recordId, storedFabricHash, ledgerHash);
                return VerificationResult.TAMPERED;
            }

            if (!currentDbHash.equals(ledgerHash)) {
                // The current DB row hashes differently from what was committed
                // This means the DB row content was modified after Fabric commit
                log.error("[TamperDetection] TAMPER ALERT — DB row modified after commit! " +
                                "recordId={} currentHash={} ledgerHash={}",
                        recordId, currentDbHash, ledgerHash);
                return VerificationResult.TAMPERED;
            }

            log.debug("[TamperDetection] OK recordId={}", recordId);
            return VerificationResult.OK;

        } catch (Exception e) {
            log.error("[TamperDetection] Error verifying recordId={}: {}", recordId, e.getMessage());
            return VerificationResult.ERROR;
        }
    }

    private enum VerificationResult { OK, TAMPERED, MISSING, ERROR }
}