package com.peterscode.edutrack.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesGcmDestinationCipher implements DestinationCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String CIPHER = "AES/GCM/NoPadding";

    private final byte[] key;

    public AesGcmDestinationCipher(
            @Value("${edutrack.notifications.destination-encryption-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null;
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey);
            if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
                throw new IllegalStateException("Notification encryption key must be a base64 AES key");
            }
            this.key = decoded;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Notification encryption key must be a base64 AES key");
        }
    }

    @Override
    public String encrypt(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Destination is required");
        }
        byte[] encryptionKey = requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt notification destination");
        }
    }

    @Override
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new IllegalArgumentException("Encrypted destination is required");
        }
        byte[] encryptionKey = requireKey();
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedValue);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted destination");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to decrypt notification destination");
        }
    }

    @Override
    public boolean isConfigured() {
        return key != null;
    }

    private byte[] requireKey() {
        if (key == null) {
            throw new IllegalStateException("Notification destination encryption is not configured");
        }
        return key;
    }
}
