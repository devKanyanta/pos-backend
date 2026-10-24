package com.devkanyanta.pos.sync.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.devkanyanta.pos.payment.dto.CreatePaymentRequest;

import lombok.Getter;
import lombok.Setter;

/** A sale recorded on the device while offline. */
@Getter
@Setter
public class OfflineSaleRequest {

    /** Unique local id (e.g. UUID) – the idempotency key. Required. */
    private String clientSaleId;

    /** Receipt number printed by the device, if any. */
    private String clientReceiptNumber;

    /** Device-local date/time of the sale (business time zone). Required. */
    private LocalDateTime soldAt;

    private Long customerId;

    private String notes;

    private BigDecimal discountAmount;

    /** Total calculated on the device; used to detect calculation drift. */
    private BigDecimal expectedTotal;

    private List<Item> items;

    private List<CreatePaymentRequest> payments;

    @Getter
    @Setter
    public static class Item {

        private Long productId;

        /** Snapshot from the device catalogue (used if the product no longer exists). */
        private String productName;

        private Integer quantity;

        /** Price charged on the device (VAT exclusive). */
        private BigDecimal unitPrice;

        private BigDecimal vatRate;

        private String zraTaxType;

        private BigDecimal discountAmount;
    }
}
