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

    /** Optional new unit cost; updates the product's cost price. */
    private BigDecimal unitCost;

    private String notes;
}
