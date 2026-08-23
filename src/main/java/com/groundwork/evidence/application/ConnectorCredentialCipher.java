package com.groundwork.evidence.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ConnectorCredentialCipher {
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private final int currentVersion;
    private final Map<Integer, SecretKeySpec> keys;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public ConnectorCredentialCipher(
            @Value("${groundwork.connectors.credential-key:local-development-connector-key-change-me}") String secret,
            @Value("${groundwork.connectors.credential-key-version:1}") int version,
            @Value("${groundwork.connectors.previous-credential-key:}") String previousSecret,
            @Value("${groundwork.connectors.previous-credential-key-version:0}") int previousVersion) {
        if (version < 1) throw new IllegalArgumentException("Connector credential key version must be positive");
        this.currentVersion = version;
        this.keys = new LinkedHashMap<>();
        this.keys.put(version, deriveKey(secret));
        if (previousSecret != null && !previousSecret.isBlank()) {
            if (previousVersion < 1 || previousVersion == version) {
                throw new IllegalArgumentException("Previous connector credential key needs a distinct positive version");
            }
            this.keys.put(previousVersion, deriveKey(previousSecret));
        }
    }

    /** Convenient constructor for focused unit tests and non-Spring callers. */
    public ConnectorCredentialCipher(String secret) {
        this(secret, 1, "", 0);
    }

    public int currentVersion() {
        return currentVersion;
    }

    private SecretKeySpec deriveKey(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Connector credential key must contain at least 32 characters");
        }
        try {
            return new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES credential encryption is unavailable", exception);
        }
    }

    public String encrypt(String plaintext, String context) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentVersion), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v" + currentVersion + ":" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Connector credential encryption failed", exception);
        }
    }

    public String decrypt(String encoded, String context) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            int separator = encoded.indexOf(':');
            if (separator < 2 || encoded.charAt(0) != 'v') {
                throw new IllegalArgumentException("Encrypted credential version is malformed");
            }
            int version = Integer.parseInt(encoded.substring(1, separator));
            SecretKeySpec key = keys.get(version);
            if (key == null) throw new IllegalArgumentException("Unsupported connector credential version v" + version);
            byte[] value = Base64.getUrlDecoder().decode(encoded.substring(separator + 1));
            if (value.length <= NONCE_BYTES) throw new IllegalArgumentException("Encrypted credential is malformed");
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] encrypted = new byte[value.length - NONCE_BYTES];
            System.arraycopy(value, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(value, NONCE_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Connector credential decryption failed", exception);
        }
    }
}
