package com.devkanyanta.pos.inventory.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryMovementResponse {

    private Long id;

    private Long productId;

    private String productName;

    private String movementType;

    private Integer quantity;

    private Long referenceId;

    private String referenceType;

    private String notes;

    /** Adjustment reason code (DAMAGED, EXPIRED, ...), where applicable. */
    private String reason;

    /** Cost per unit the movement was valued at. */
    private java.math.BigDecimal unitCost;

    /** Signed value of the movement (quantity x unitCost). */
    private java.math.BigDecimal value;

    /** On-hand quantity right after the movement. */
    private Integer balanceAfter;

    private String requestId;

    private String performedBy;

    private LocalDateTime createdAt;
}
