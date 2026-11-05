package com.peterscode.edutrack.notification.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmDestinationCipherTest {

    private static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAA==";

    @Test
    void encryptedDestinationRoundTripsWithoutStoringPlaintext() {
        AesGcmDestinationCipher cipher = new AesGcmDestinationCipher(TEST_KEY);

        String encrypted = cipher.encrypt("test-destination");

        assertThat(encrypted).isNotEqualTo("test-destination");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("test-destination");
    }

    @Test
    void missingKeyFailsOnlyWhenEncryptionIsUsed() {
        AesGcmDestinationCipher cipher = new AesGcmDestinationCipher("");

        assertThatThrownBy(() -> cipher.encrypt("test-destination"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Notification destination encryption is not configured");
    }
}
