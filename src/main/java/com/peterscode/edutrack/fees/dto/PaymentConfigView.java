package com.peterscode.edutrack.fees.dto;

import com.peterscode.edutrack.fees.enums.ShortcodeType;

import java.time.LocalDateTime;

/**
 * Response view for school Mpesa payment configuration (credentials excluded).
 */
public record PaymentConfigView(
        Long id,
        Long schoolId,
        String shortcode,
        ShortcodeType shortcodeType,
        String environment,
        Boolean enabled,
        Boolean credentialsSet,
        LocalDateTime createdAt
) {}
