package com.devkanyanta.pos.sale.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SaleItemResponse {

    private Long id;

    private Long productId;

    private String productName;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal lineTotal;

    private BigDecimal vatRate;

    private BigDecimal vatAmount;

    private BigDecimal discountAmount;

    private String itemStatus;

    private Integer refundedQuantity;
}
