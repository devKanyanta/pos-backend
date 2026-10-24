package com.devkanyanta.pos.sale.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefundResponse {

    private Long id;

    private String refundNumber;

    private BigDecimal totalAmount;

    private BigDecimal vatAmount;

    private String reason;

    private String zraReasonCode;

    private String createdBy;

    private LocalDateTime createdAt;

    /** ZRA credit note status: PENDING, RETRYING, SUCCESS, FAILED, or null when not reported. */
    private String zraStatus;

    private String zraReceiptNumber;

    private List<Line> items;

    private List<Tender> payouts;

    @Getter
    @Builder
    public static class Line {

        private Long saleItemId;

        private String productName;

        private Integer quantity;

        private BigDecimal totalAmount;

        private BigDecimal vatAmount;
    }

    @Getter
    @Builder
    public static class Tender {

        private Long paymentId;

        private Long paymentMethodId;

        private String paymentMethodName;

        private BigDecimal amount;
    }
}
