package com.forensic.audit.crypto;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static Logger log = LoggerFactory.getLogger(CryptoService.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final String BC = BouncyCastleProvider.PROVIDER_NAME;
    private static final int GCM_TAG_BITS = 128;

    private volatile PrivateKey privateKey;
    private volatile PublicKey publicKey;

    @PostConstruct
    public void init() {
        // Wrap checked exceptions — @PostConstruct silently swallows them,
        // so we rethrow as unchecked to guarantee a visible startup failure.
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BC);
            kpg.initialize(2048, new SecureRandom());
            KeyPair kp = kpg.generateKeyPair();
            this.privateKey = kp.getPrivate();
            this.publicKey = kp.getPublic();
            log.info("RSA-2048 key pair generated.");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to generate RSA key pair at startup", e);
        }
    }

    public String getPublicKeyPem() {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----";
    }

    public String decryptUbaEnvelope(String encryptedData, String encryptedAesKey, String iv)
            throws GeneralSecurityException {

        byte[] wrappedKey = Base64.getDecoder().decode(encryptedAesKey);
        byte[] ciphertext = Base64.getDecoder().decode(encryptedData);
        byte[] ivBytes    = Base64.getDecoder().decode(iv);

        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", BC);
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] aesKeyBytes = rsaCipher.doFinal(wrappedKey);

        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding", BC);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, ivBytes));

        return new String(aesCipher.doFinal(ciphertext));
    }
}
