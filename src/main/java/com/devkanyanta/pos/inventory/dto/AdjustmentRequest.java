package com.devkanyanta.pos.inventory.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdjustmentRequest {

    private Long productId;

    private Integer quantity;

    /** Free-text explanation (required). */
    private String reason;

    /**
     * Why stock changed: DAMAGED, EXPIRED, LOST, FOUND, CORRECTION (default) or OTHER. Used by the
     * adjustment report.
     */
    private String reasonCode;

    /** Optional idempotency key (8-64 chars). Resubmitting it returns the first result without adjusting again. */
    private String requestId;
}
