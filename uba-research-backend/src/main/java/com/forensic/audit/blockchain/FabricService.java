package com.forensic.audit.blockchain;

import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.*;
import org.hyperledger.fabric.client.identity.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

@Slf4j
@Service
public class FabricService {

    @Value("${fabric.peer.endpoint:localhost:7051}")
    private String peerEndpoint;

    @Value("${fabric.peer.tlsCertPath:#{null}}")
    private String tlsCertPath;

    @Value("${fabric.msp.certPath:#{null}}")
    private String mspCertPath;

    @Value("${fabric.msp.keyPath:#{null}}")
    private String mspKeyPath;

    @Value("${fabric.msp.id:Org1MSP}")
    private String mspId;

    @Value("${fabric.channel.name:mychannel}")
    private String channelName;

    @Value("${fabric.chaincode.name:uba-audit}")
    private String chaincodeName;

    private ManagedChannel grpcChannel;
    private Gateway gateway;

    @PostConstruct
    public void connect() {
        // Only connect if cert paths are configured — allows app to start without Fabric in dev
        if (mspCertPath == null || mspKeyPath == null || tlsCertPath == null) {
            log.warn("[Fabric] Cert paths not configured — Fabric Gateway will not connect. Set fabric.* properties.");
            return;
        }
        try {
            X509Certificate certificate = Identities.readX509Certificate(Files.newBufferedReader(Path.of(mspCertPath)));
            PrivateKey privateKey       = Identities.readPrivateKey(Files.newBufferedReader(Path.of(mspKeyPath)));

            grpcChannel = NettyChannelBuilder.forTarget(peerEndpoint)
                    .sslContext(GrpcSslContexts.forClient()
                            .trustManager(Path.of(tlsCertPath).toFile())
                            .build())
                    .build();

            gateway = Gateway.newInstance()
                    .identity(new X509Identity(mspId, certificate))
                    .signer(Signers.newPrivateKeySigner(privateKey))
                    .connection(grpcChannel)
                    .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                    .submitOptions(options -> options.withDeadlineAfter(10, TimeUnit.SECONDS))
                    .connect();

            log.info("[Fabric] Gateway connected to {}", peerEndpoint);
        } catch (Exception e) {
            log.error("[Fabric] Failed to connect Gateway: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void disconnect() throws InterruptedException {
        if (gateway != null) gateway.close();
        if (grpcChannel != null) grpcChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Async
    public void logUbaAudit(String ubaTelemetry, String userEmail) {
        try {
            String hashHex = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(ubaTelemetry.getBytes(StandardCharsets.UTF_8))
            );

            if (gateway == null) {
                log.warn("[Fabric] Gateway not connected — audit skipped. User={}, Hash={}", userEmail, hashHex);
                return;
            }

            Network network   = gateway.getNetwork(channelName);
            Contract contract = network.getContract(chaincodeName);
            contract.submitTransaction("LogAudit", userEmail, hashHex, ubaTelemetry);

            log.info("[Fabric] Audit submitted. User={}, Hash={}", userEmail, hashHex);

        } catch (Exception e) {
            log.error("[Fabric] Audit submission failed for {}: {}", userEmail, e.getMessage());
        }
    }
}
