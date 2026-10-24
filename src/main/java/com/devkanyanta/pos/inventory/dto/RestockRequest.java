package com.devkanyanta.pos.inventory.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestockRequest {

    private Long productId;

    /** Units received; must be positive. */
    private Integer quantity;

    /** Unit cost of this delivery. Re-averages the product's weighted-average cost and becomes its latest purchase cost. */
    private BigDecimal unitCost;

    private String notes;

    /** Optional idempotency key (8-64 chars). Resubmitting it returns the first result without receiving again. */
    private String requestId;
}
