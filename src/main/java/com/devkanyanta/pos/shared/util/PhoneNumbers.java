package com.devkanyanta.pos.shared.util;

import org.springframework.util.StringUtils;

import com.devkanyanta.pos.shared.exception.BadRequestException;

/**
 * Normalises phone numbers to international digits-only form (e.g. 260971234567),
 * as required by WhatsApp links and messaging APIs.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static String normalize(String raw, String defaultCountryCode) {
        if (!StringUtils.hasText(raw)) {
            throw new BadRequestException("Phone number is required");
        }

        String digits = raw.trim().replaceAll("[\\s\\-().]", "");

        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        } else if (digits.startsWith("00")) {
            digits = digits.substring(2);
        } else if (digits.startsWith("0") && StringUtils.hasText(defaultCountryCode)) {
            digits = defaultCountryCode + digits.substring(1);
        }

        if (!digits.matches("\\d{8,15}")) {
            throw new BadRequestException("Invalid phone number: " + raw);
        }

        return digits;
    }
}
