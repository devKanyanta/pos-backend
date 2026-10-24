package com.devkanyanta.pos.shared.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.devkanyanta.pos.shared.exception.BadRequestException;

/**
 * Single source of truth for how barcode values are cleaned, validated and compared.
 *
 * <p>Stored barcodes are upper-case, have no surrounding whitespace and consist only of letters,
 * digits and {@code . _ / + -} (3 to 64 characters). That covers EAN-13/EAN-8, UPC-A/UPC-E, ITF-14
 * and typical Code 128 shop labels. Check digits are deliberately not enforced because shops
 * routinely print their own internal codes.
 */
public final class Barcodes {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 64;

    private static final Pattern VALID = Pattern.compile("[A-Z0-9._/+\\-]{" + MIN_LENGTH + "," + MAX_LENGTH + "}");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /** Spreadsheets turn long numeric codes into 6.00123E+12; the original digits are already lost. */
    private static final Pattern SCIENTIFIC = Pattern.compile("\\d+(\\.\\d+)?E[+-]?\\d+");

    private Barcodes() {
    }

    /**
     * Lenient form used for lookups: trimmed and upper-cased, or null when blank. Never throws, so
     * legacy values that would no longer pass {@link #normalize} can still be found.
     */
    public static String canonical(String raw) {
        String trimmed = Texts.clean(raw);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /** True when {@code canonical} is a storable barcode. */
    public static boolean isValid(String canonical) {
        return canonical != null && VALID.matcher(canonical).matches();
    }

    /**
     * Strict form used when a barcode is saved: returns the canonical value, null for a blank input
     * (meaning "no barcode"), and throws a 400 for anything that cannot be stored.
     */
    public static String normalize(String raw) {
        String canonical = canonical(raw);
        if (canonical == null) {
            return null;
        }
        if (SCIENTIFIC.matcher(canonical).matches()) {
            throw new BadRequestException("Invalid barcode '" + canonical + "': it looks like a number in scientific "
                    + "notation (a spreadsheet export). Format the barcode column as text so the digits are kept.");
        }
        if (!isValid(canonical)) {
            throw new BadRequestException("Invalid barcode '" + Texts.truncate(canonical, 70) + "'. Use "
                    + MIN_LENGTH + "-" + MAX_LENGTH + " letters, digits or . _ / + - (no spaces).");
        }
        return canonical;
    }

    /**
     * Values that identify the same physical product as {@code canonical}. A UPC-A code (12 digits)
     * is the same as the EAN-13 with a leading zero, and scanners report either form.
     */
    public static List<String> equivalents(String canonical) {
        if (canonical == null) {
            return List.of();
        }
        if (canonical.length() == 12 && DIGITS.matcher(canonical).matches()) {
            return List.of(canonical, "0" + canonical);
        }
        if (canonical.length() == 13 && canonical.charAt(0) == '0' && DIGITS.matcher(canonical).matches()) {
            return List.of(canonical, canonical.substring(1));
        }
        return List.of(canonical);
    }
}
