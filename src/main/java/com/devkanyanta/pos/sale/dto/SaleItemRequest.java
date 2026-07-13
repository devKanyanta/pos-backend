package com.devkanyanta.pos.sale.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaleItemRequest {

    private Long productId;

    private Integer quantity;

    private BigDecimal discountAmount;
}
