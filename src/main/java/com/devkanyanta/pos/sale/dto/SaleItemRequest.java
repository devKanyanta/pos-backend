package com.devkanyanta.pos.sale.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaleItemRequest {

    /** Product id; optional when {@link #barcode} identifies the product. */
    private Long productId;

    /**
     * Scanned or typed barcode, resolved on the server. If {@link #productId} is also sent, both must
     * refer to the same product.
     */
    private String barcode;

    private Integer quantity;

    private BigDecimal discountAmount;
}
