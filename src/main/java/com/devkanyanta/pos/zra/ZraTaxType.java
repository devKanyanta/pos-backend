package com.devkanyanta.pos.zra;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * ZRA Smart Invoice VAT categories ({@code vatCatCd}). Each product is mapped to one category;
 * the category fixes the VAT rate used on invoices and the taxable-amount bucket
 * ({@code taxblAmtX}/{@code taxAmtX}) the line is reported under.
 */
public enum ZraTaxType {

    A("A", "16.00", "Standard rated", "A"),
    B("B", "16.00", "Minimum taxable value (MTV)", "B"),
    C1("C1", "0.00", "Exports (zero-rated)", "C1"),
    C2("C2", "0.00", "Zero-rated local purchase order", "C2"),
    C3("C3", "0.00", "Zero-rated by nature", "C3"),
    D("D", "0.00", "Exempt", "D"),
    E("E", "0.00", "Disbursement", "E"),
    RVAT("RVAT", "16.00", "Reverse VAT", "Rvat");

    private final String code;

    private final BigDecimal rate;

    private final String label;

    private final String payloadSuffix;

    ZraTaxType(String code, String rate, String label, String payloadSuffix) {
        this.code = code;
        this.rate = new BigDecimal(rate);
        this.label = label;
        this.payloadSuffix = payloadSuffix;
    }

    public String getCode() {
        return code;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getLabel() {
        return label;
    }

    /** Suffix of the VSDC totals fields, e.g. {@code taxblAmtRvat}. */
    public String getPayloadSuffix() {
        return payloadSuffix;
    }

    public boolean isZeroRated() {
        return this == C1 || this == C2 || this == C3;
    }

    public boolean isExempt() {
        return this == D;
    }

    public static ZraTaxType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized) || type.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new com.devkanyanta.pos.shared.exception.BadRequestException(
                        "Unknown ZRA tax type '" + code + "'. Allowed: A, B, C1, C2, C3, D, E, RVAT"));
    }

    /**
     * Default mapping for products that only specify a VAT rate: 16% is standard rated (A) and
     * 0% is treated as exempt (D). Zero-rated goods must be given an explicit C1/C2/C3 type.
     */
    public static ZraTaxType defaultForRate(BigDecimal vatRate) {
        if (vatRate == null) {
            return A;
        }
        if (vatRate.compareTo(A.rate) == 0) {
            return A;
        }
        if (vatRate.signum() == 0) {
            return D;
        }
        return null;
    }
}
