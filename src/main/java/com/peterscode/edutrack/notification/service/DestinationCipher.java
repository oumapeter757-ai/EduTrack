package com.peterscode.edutrack.notification.service;

public interface DestinationCipher {
    String encrypt(String value);
    String decrypt(String encryptedValue);

    default boolean isConfigured() {
        return true;
    }
}
