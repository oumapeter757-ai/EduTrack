package com.peterscode.edutrack.shared;

import java.util.regex.Pattern;

public final class PhoneNumberNormalizer {
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private PhoneNumberNormalizer() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String raw = value.trim().replaceAll("[()\\s.-]", "");
        if (raw.startsWith("00")) raw = "+" + raw.substring(2);
        if (raw.startsWith("0") && raw.length() == 10) raw = "+254" + raw.substring(1);
        if (!raw.startsWith("+")) raw = "+254" + raw;
        if (!E164.matcher(raw).matches()) {
            throw new IllegalArgumentException("Invalid phone number");
        }
        return raw;
    }
}
