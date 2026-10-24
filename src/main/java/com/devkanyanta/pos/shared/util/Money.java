package com.devkanyanta.pos.shared.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Currency helpers. All monetary values are Zambian Kwacha with 2 decimal places,
 * rounded half-up.
 */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentOf(BigDecimal amount, BigDecimal ratePercent) {
        if (amount == null || ratePercent == null) {
            return ZERO;
        }
        return amount.multiply(ratePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public static String format(BigDecimal value) {
        return of(value).toPlainString();
    }
}
