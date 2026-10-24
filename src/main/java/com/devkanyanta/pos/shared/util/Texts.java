package com.devkanyanta.pos.shared.util;

import org.springframework.util.StringUtils;

public final class Texts {

    private Texts() {
    }

    /** Trims the value and converts blank strings to null. */
    public static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
