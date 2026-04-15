package com.forensic.audit.blockchain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
public class FabricService {

    @Async
    public void logUbaAudit(String ubaTelemetry, String userEmail) {
        try {
            // Calculate SHA-256 hash of telemetry
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ubaTelemetry.getBytes(StandardCharsets.UTF_8));
            String hashHex = bytesToHex(hash);

            // Simulate submitting to Hyperledger Fabric
//            log.info("Submitting to blockchain: User={}, Hash={}", userEmail, hashHex);
            // Add Fabric Gateway SDK logic here
        } catch (NoSuchAlgorithmException e) {
//            log.error("Error calculating SHA-256 hash", e);
            System.out.println("Error calculating SHA-256 hash: " + e.getMessage());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}