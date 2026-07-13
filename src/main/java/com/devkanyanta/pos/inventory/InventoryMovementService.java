package com.devkanyanta.pos.inventory;

import java.util.List;

import com.devkanyanta.pos.inventory.dto.AdjustmentRequest;
import com.devkanyanta.pos.inventory.dto.InventoryMovementResponse;

public interface InventoryMovementService {

    List<InventoryMovementResponse> getAllMovements();

    List<InventoryMovementResponse> getMovementsByProductId(Long productId);

    InventoryMovementResponse adjustStock(AdjustmentRequest request, String username);
}
