package com.devkanyanta.pos.inventory;

import org.springframework.stereotype.Component;

import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * Single place where product stock changes. Every change updates the stock snapshot on the
 * product and appends an immutable {@link InventoryMovementEntity} to the audit trail.
 * Callers must hold a transaction and should load the product with
 * {@link ProductRepository#findByIdForUpdate(Long)} to serialise concurrent updates.
 */
@Component
@RequiredArgsConstructor
public class StockLedger {

    private final ProductRepository productRepository;

    private final InventoryMovementRepository inventoryMovementRepository;

    public InventoryMovementEntity record(
            ProductEntity product,
            MovementType type,
            int quantityChange,
            Long referenceId,
            String referenceType,
            String notes,
            String performedBy
    ) {
        int current = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        product.setStockQuantity(current + quantityChange);
        productRepository.save(product);

        return inventoryMovementRepository.save(InventoryMovementEntity.builder()
                .product(product)
                .movementType(type)
                .quantity(quantityChange)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .notes(notes != null && notes.length() > 255 ? notes.substring(0, 255) : notes)
                .performedBy(performedBy != null ? performedBy : "system")
                .build());
    }
}
