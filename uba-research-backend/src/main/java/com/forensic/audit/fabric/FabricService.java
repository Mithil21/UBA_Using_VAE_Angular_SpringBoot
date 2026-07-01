package com.forensic.audit.fabric;

import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.*;
import org.hyperledger.fabric.client.identity.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

/**
 * FabricService — Spring Boot integration with Hyperledger Fabric.
 *
 * Connects to the Fabric Gateway on peer0.org1 and provides two operations:
 *   - commitRecord()  — called after every VAE decision (accept/review/reject)
 *   - verifyRecord()  — called by TamperDetectionScheduler to verify DB integrity
 *
 * The Fabric ledger provides an append-only, tamper-evident audit trail.
 * Any post-hoc modification to PostgreSQL rows is detectable as a hash mismatch
 * against the immutable ledger entry.
 */
@Slf4j
@Service
public class FabricService {

    private static final String CHANNEL_NAME      = "auditchannel";
    private static final String CHAINCODE_NAME     = "auditcontract";
    private static final String MSP_ID             = "Org1MSP";
    private static final String PEER_ENDPOINT      = "localhost:7051";
    private static final String PEER_HOST_OVERRIDE = "peer0.org1.example.com";

    // Paths to Fabric crypto material
    // Adjust base path if fabric-samples is not in your home directory
    private static final String CRYPTO_BASE =
            System.getProperty("user.home") +
                    "/fabric-samples/test-network/organizations/peerOrganizations/" +
                    "org1.example.com";

    private static final String CERT_PATH =
            CRYPTO_BASE + "/users/User1@org1.example.com/msp/signcerts/User1@org1.example.com-cert.pem";

    private static final String KEY_DIR =
            CRYPTO_BASE + "/users/User1@org1.example.com/msp/keystore";

    private static final String TLS_CERT_PATH =
            CRYPTO_BASE + "/peers/peer0.org1.example.com/tls/ca.crt";

    private ManagedChannel grpcChannel;
    private Gateway        gateway;
    private Contract       contract;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        try {
            log.info("[Fabric] Initialising connection to peer {}", PEER_ENDPOINT);

            // TLS channel credentials using the peer's TLS CA cert
            ChannelCredentials tlsCredentials = TlsChannelCredentials.newBuilder()
                    .trustManager(Paths.get(TLS_CERT_PATH).toFile())
                    .build();

            grpcChannel = Grpc.newChannelBuilder(PEER_ENDPOINT, tlsCredentials)
                    .overrideAuthority(PEER_HOST_OVERRIDE)
                    .build();

            // Load identity and signing key
            Identity identity = new X509Identity(MSP_ID, loadCertificate());
            Signer   signer   = Signers.newPrivateKeySigner(loadPrivateKey());

            gateway = Gateway.newInstance()
                    .identity(identity)
                    .signer(signer)
                    .connection(grpcChannel)
                    .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                    .submitOptions(options  -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                    .connect();

            Network  network = gateway.getNetwork(CHANNEL_NAME);
            contract = network.getContract(CHAINCODE_NAME);

            log.info("[Fabric] Connected — channel={} chaincode={}", CHANNEL_NAME, CHAINCODE_NAME);

        } catch (Exception e) {
            // Log but don't crash Spring Boot — Fabric is an audit layer, not core logic
            log.error("[Fabric] Failed to initialise — audit trail will be unavailable: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (gateway    != null) gateway.close();
            if (grpcChannel != null) grpcChannel.shutdownNow()
                    .awaitTermination(5, TimeUnit.SECONDS);
            log.info("[Fabric] Connection closed");
        } catch (Exception e) {
            log.warn("[Fabric] Error during cleanup: {}", e.getMessage());
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Commit an audit record to the Fabric ledger.
     * Called by VaeRequestConsumer after every VAE decision.
     *
     * @param recordId     UUID from uba_accepted / uba_review / uba_rejected
     * @param email        User's email address
     * @param decision     ACCEPTED, REVIEW, or REJECTED
     * @param combinedHash SHA-256(db_row) for tamper detection
     * @param vaeScore     VAE probability score
     * @param mseScore     VAE reconstruction error
     * @param ipAddress    Request IP address
     * @return true if committed successfully, false if Fabric unavailable
     */
    public boolean commitRecord(String recordId, String email, String decision,
                                String combinedHash, float vaeScore, float mseScore,
                                String ipAddress) {
        if (contract == null) {
            log.warn("[Fabric] Contract not initialised — skipping commit for recordId={}", recordId);
            return false;
        }

        try {
            contract.submitTransaction(
                    "CommitRecord",
                    recordId,
                    email,
                    decision,
                    combinedHash,
                    String.valueOf(vaeScore),
                    String.valueOf(mseScore),
                    ipAddress != null ? ipAddress : "unknown"
            );

            log.info("[Fabric] Committed recordId={} decision={} hash={}",
                    recordId, decision, combinedHash.substring(0, Math.min(8, combinedHash.length())) + "...");
            return true;

        } catch (Exception e) {
            log.error("[Fabric] Failed to commit recordId={}: {}", recordId, e.getMessage());
            return false;
        }
    }

    /**
     * Verify a record on the ledger — returns stored hash for comparison.
     * Called by TamperDetectionScheduler.
     *
     * @param recordId UUID to look up
     * @return JSON string of the ledger record, or null if not found / unavailable
     */
    public String verifyRecord(String recordId) {
        if (contract == null) {
            log.warn("[Fabric] Contract not initialised — skipping verify for recordId={}", recordId);
            return null;
        }

        try {
            byte[] result = contract.evaluateTransaction("VerifyRecord", recordId);
            return new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[Fabric] Failed to verify recordId={}: {}", recordId, e.getMessage());
            return null;
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private java.security.cert.X509Certificate loadCertificate()
            throws IOException, CertificateException {
        String certPem = Files.readString(Path.of(CERT_PATH));
        return Identities.readX509Certificate(certPem);
    }

    private java.security.PrivateKey loadPrivateKey()
            throws IOException, InvalidKeyException {
        // Keystore directory contains one private key file
        Path keyDir = Path.of(KEY_DIR);
        Path keyFile = Files.list(keyDir)
                .filter(p -> p.toString().endsWith("_sk"))
                .findFirst()
                .orElseThrow(() -> new IOException("No private key found in " + KEY_DIR));

        String keyPem = Files.readString(keyFile);
        return Identities.readPrivateKey(keyPem);
    }
}