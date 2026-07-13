package com.devkanyanta.pos.inventory.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdjustmentRequest {

    private Long productId;

    private Integer quantity;

    private String reason;
}
