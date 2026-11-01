package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.entity.SchoolPaymentConfig;
import com.peterscode.edutrack.fees.enums.MpesaEnvironment;
import com.peterscode.edutrack.fees.enums.ShortcodeType;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.repository.SchoolPaymentConfigRepository;
import com.peterscode.edutrack.fees.security.FeesAccessGuard;
import com.peterscode.edutrack.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

/**
 * Manages per-school Mpesa payment configuration.
 * Only SCHOOL_ADMIN can create, update, or rotate credentials.
 * ACCOUNTANT can view config but never sees credentials.
 */
@Service
@RequiredArgsConstructor
public class SchoolPaymentConfigService {

    private final SchoolPaymentConfigRepository configRepository;
    private final CredentialEncryptionService encryptionService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Get the payment config for the current school.
     */
    @Transactional(readOnly = true)
    public Optional<SchoolPaymentConfig> getConfig(UserPrincipal principal) {
        FeesAccessGuard.assertAdminOrAccountant(principal);
        return configRepository.findBySchoolId(principal.getSchoolId());
    }

    /**
     * Create or update the payment config for the current school.
     * Only SCHOOL_ADMIN can set credentials.
     */
    @Transactional
    public SchoolPaymentConfig upsertConfig(UserPrincipal principal,
            String shortcode, ShortcodeType shortcodeType, MpesaEnvironment environment,
            String consumerKey, String consumerSecret, String passkey) {
        FeesAccessGuard.assertAdmin(principal);
        Long schoolId = principal.getSchoolId();

        // Check shortcode uniqueness
        if (configRepository.existsByShortcodeAndSchoolIdNot(shortcode, schoolId)) {
            throw new FeesRuleException("CONFIG_INVALID", BAD_REQUEST,
                    "Shortcode already used by another school");
        }

        SchoolPaymentConfig config = configRepository.findBySchoolId(schoolId)
                .orElseGet(() -> SchoolPaymentConfig.builder()
                        .schoolId(schoolId)
                        .callbackTokenHash(generateCallbackTokenHash())
                        .build());

        config.setShortcode(shortcode);
        config.setShortcodeType(shortcodeType);
        config.setEnvironment(environment);
        config.setEncryptedConsumerKey(encryptionService.encrypt(consumerKey));
        config.setEncryptedConsumerSecret(encryptionService.encrypt(consumerSecret));
        config.setEncryptedPasskey(encryptionService.encrypt(passkey));

        return configRepository.save(config);
    }

    /**
     * Enable or disable the payment config.
     */
    @Transactional
    public SchoolPaymentConfig toggleEnabled(UserPrincipal principal, boolean enabled) {
        FeesAccessGuard.assertAdmin(principal);
        SchoolPaymentConfig config = getConfigOrThrow(principal);
        config.setEnabled(enabled);
        return configRepository.save(config);
    }

    /**
     * Rotate the callback token. The old token stops working immediately.
     */
    @Transactional
    public String rotateCallbackToken(UserPrincipal principal) {
        FeesAccessGuard.assertAdmin(principal);
        SchoolPaymentConfig config = getConfigOrThrow(principal);
        String newToken = generateCallbackToken();
        config.setCallbackTokenHash(hashToken(newToken));
        configRepository.save(config);
        // Return the token once — admin must record it
        return newToken;
    }

    /**
     * Test the sandbox connection. Returns true if credentials are valid.
     */
    @Transactional(readOnly = true)
    public boolean testConnection(UserPrincipal principal) {
        FeesAccessGuard.assertAdmin(principal);
        SchoolPaymentConfig config = getConfigOrThrow(principal);
        if (config.getEnvironment() != MpesaEnvironment.SANDBOX) {
            throw new FeesRuleException("CONFIG_INVALID", BAD_REQUEST,
                    "Test connection only works in SANDBOX mode");
        }
        // In a real implementation, this would call the Daraja API to validate credentials.
        // For now, we just verify the credentials are present.
        String key = encryptionService.decrypt(config.getEncryptedConsumerKey());
        String secret = encryptionService.decrypt(config.getEncryptedConsumerSecret());
        return key != null && !key.isBlank() && secret != null && !secret.isBlank();
    }

    /**
     * Get config by callback token hash (used by webhook endpoints).
     */
    @Transactional(readOnly = true)
    public Optional<SchoolPaymentConfig> getByCallbackToken(String tokenHash) {
        return configRepository.findByCallbackTokenHash(tokenHash);
    }

    /**
     * Get config by shortcode (used by C2B matching).
     */
    @Transactional(readOnly = true)
    public Optional<SchoolPaymentConfig> getByShortcode(String shortcode) {
        return configRepository.findByShortcode(shortcode);
    }

    /**
     * Get config by school ID (used by C2B validation callback).
     */
    @Transactional(readOnly = true)
    public Optional<SchoolPaymentConfig> getConfigBySchoolId(Long schoolId) {
        return configRepository.findBySchoolId(schoolId);
    }

    private SchoolPaymentConfig getConfigOrThrow(UserPrincipal principal) {
        return configRepository.findBySchoolId(principal.getSchoolId())
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Payment config not found"));
    }

    private String generateCallbackToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCallbackTokenHash() {
        String token = generateCallbackToken();
        return hashToken(token);
    }

    private String hashToken(String token) {
        // Simple SHA-256 hash for token storage
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Token hashing failed", e);
        }
    }
}
