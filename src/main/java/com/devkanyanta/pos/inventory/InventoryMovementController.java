package com.devkanyanta.pos.inventory;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.inventory.dto.AdjustmentRequest;
import com.devkanyanta.pos.inventory.dto.InventoryMovementResponse;
import com.devkanyanta.pos.inventory.dto.RestockRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/inventory-movements")
@RequiredArgsConstructor
public class InventoryMovementController {

    private final InventoryMovementService inventoryMovementService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.INVENTORY_VIEW + "')")
    public List<InventoryMovementResponse> getAllMovements() {
        return inventoryMovementService.getAllMovements();
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.INVENTORY_VIEW + "')")
    public List<InventoryMovementResponse> getMovementsByProductId(
            @PathVariable Long productId
    ) {
        return inventoryMovementService.getMovementsByProductId(productId);
    }

    @PostMapping("/adjustment")
    @PreAuthorize("hasAuthority('" + PermissionConstants.INVENTORY_ADJUST + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryMovementResponse adjustStock(
            @RequestBody AdjustmentRequest request,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "system";
        return inventoryMovementService.adjustStock(request, username);
    }

    @PostMapping("/restock")
    @PreAuthorize("hasAuthority('" + PermissionConstants.INVENTORY_ADJUST + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryMovementResponse restock(
            @RequestBody RestockRequest request,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "system";
        return inventoryMovementService.restock(request, username);
    }
}
