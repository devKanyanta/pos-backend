package com.devkanyanta.pos.sync.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SyncSaleResult {

    private String clientSaleId;

    /**
     * CREATED – recorded now; DUPLICATE – already recorded earlier (safe to drop from the queue);
     * REJECTED – invalid, see {@link #error} (keep for user attention, do not resend unchanged).
     */
    private String status;

    private Long saleId;

    private String saleNumber;

    private String receiptNumber;

    private BigDecimal totalAmount;

    private String paymentStatus;

    private List<String> conflicts;

    private String error;
}
