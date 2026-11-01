package com.peterscode.edutrack.fees.dto;

import com.peterscode.edutrack.fees.enums.MpesaEnvironment;
import com.peterscode.edutrack.fees.enums.ShortcodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to create or update school Mpesa payment configuration.
 */
public record CreatePaymentConfigRequestDto(
        @NotBlank String shortcode,
        @NotNull ShortcodeType shortcodeType,
        @NotNull MpesaEnvironment environment,
        @NotBlank String consumerKey,
        @NotBlank String consumerSecret,
        @NotBlank String passkey
) {}
