package com.forensic.audit.crypto;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Base64;

@Slf4j
@Service
public class CryptoService {

    static {
        // Register BouncyCastle once at class load — idempotent and thread-safe
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final String BC = BouncyCastleProvider.PROVIDER_NAME;
    private static final int GCM_TAG_BITS = 128;

    // Written once at startup, read-only afterward — volatile ensures cross-thread visibility
    private volatile PrivateKey privateKey;
    private volatile PublicKey publicKey;

    @PostConstruct
    public void generateKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BC);
        kpg.initialize(2048, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();
        this.privateKey = kp.getPrivate();
        this.publicKey = kp.getPublic();
//        log.info("RSA-2048 key pair generated and stored in memory.");
    }

    /**
     * Returns the public key as a Base64-encoded PEM string for the frontend.
     */
    public String getPublicKeyPem() {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----";
    }

    /**
     * Decrypts a hybrid RSA-OAEP + AES-GCM envelope.
     * All Cipher instances are created per-call — fully thread-safe under any concurrency.
     *
     * @param encryptedData   Base64-encoded AES-GCM ciphertext
     * @param encryptedAesKey Base64-encoded RSA-OAEP wrapped AES key
     * @param iv              Base64-encoded GCM IV (12 bytes recommended)
     * @return decrypted plaintext JSON string
     */
    public String decryptUbaEnvelope(String encryptedData, String encryptedAesKey, String iv)
            throws GeneralSecurityException {

        byte[] wrappedKey  = Base64.getDecoder().decode(encryptedAesKey);
        byte[] ciphertext  = Base64.getDecoder().decode(encryptedData);
        byte[] ivBytes     = Base64.getDecoder().decode(iv);

        // Step 1: Unwrap AES key with RSA-OAEP (new Cipher per call — not thread-safe if shared)
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", BC);
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] aesKeyBytes = rsaCipher.doFinal(wrappedKey);

        // Step 2: Decrypt payload with AES-GCM
        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding", BC);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, ivBytes));

        return new String(aesCipher.doFinal(ciphertext));
    }
}
