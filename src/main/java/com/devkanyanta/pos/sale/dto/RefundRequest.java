package com.devkanyanta.pos.sale.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefundRequest {

    /** Items to refund in full (all remaining quantity). */
    private List<Long> saleItemIds;

    /** Items to refund partially, by quantity. */
    private List<RefundItemRequest> items;

    /**
     * Items from {@link #saleItemIds} whose returned goods are damaged and must not go back on the shelf.
     * They are taken back into stock and written off at once (two auditable movements).
     */
    private List<Long> damagedSaleItemIds;

    private String reason;

    /** ZRA refund reason code (rfdRsnCd); defaults to pos.zra.default-refund-reason-code. */
    private String zraReasonCode;

    /**
     * Tender used to pay the refund out (e.g. cash). When omitted the refund is returned
     * against the sale's own payments, latest first.
     */
    private Long refundPaymentMethodId;

    @Getter
    @Setter
    public static class RefundItemRequest {

        private Long saleItemId;

        private Integer quantity;

        /** SALEABLE (default) puts the goods back in stock; DAMAGED writes them off. */
        private String condition;
    }
}
