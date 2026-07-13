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

    private String performedBy;

    private LocalDateTime createdAt;
}
