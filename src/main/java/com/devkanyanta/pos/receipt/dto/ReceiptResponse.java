package com.devkanyanta.pos.receipt.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReceiptResponse {

    private Long id;

    private Long saleId;

    private String saleNumber;

    private String receiptNumber;

    private Integer printedCount;

    private LocalDateTime createdAt;

    private BigDecimal totalAmount;

    private BigDecimal vatAmount;

    /** PENDING, RETRYING, SUCCESS, FAILED, or null when the sale is not reported to ZRA. */
    private String zraStatus;

    /** True once the VSDC has signed the invoice. */
    private boolean zraVerified;

    private Long zraInvoiceNo;

    private Long zraReceiptNo;

    private String zraInternalData;

    /** Receipt signature – the verification code printed on the receipt. */
    private String zraReceiptSignature;

    private String zraSdcId;

    private String zraMrcNo;

    private String zraQrCodeUrl;

    private String zraReceiptDate;
}
