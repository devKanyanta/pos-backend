package com.devkanyanta.pos.inventory;

import java.util.List;

import com.devkanyanta.pos.inventory.dto.AdjustmentRequest;
import com.devkanyanta.pos.inventory.dto.InventoryMovementResponse;
import com.devkanyanta.pos.inventory.dto.RestockRequest;

public interface InventoryMovementService {

    List<InventoryMovementResponse> getAllMovements();

    List<InventoryMovementResponse> getMovementsByProductId(Long productId);

    InventoryMovementResponse adjustStock(AdjustmentRequest request, String username);

    InventoryMovementResponse restock(RestockRequest request, String username);
}
