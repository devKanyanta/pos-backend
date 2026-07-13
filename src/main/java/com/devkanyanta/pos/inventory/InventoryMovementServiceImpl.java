package com.devkanyanta.pos.inventory;

import java.util.List;

import org.springframework.stereotype.Service;

import com.devkanyanta.pos.inventory.dto.AdjustmentRequest;
import com.devkanyanta.pos.inventory.dto.InventoryMovementResponse;
import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryMovementServiceImpl implements InventoryMovementService {

    private final InventoryMovementRepository inventoryMovementRepository;

    private final ProductRepository productRepository;

    @Override
    public List<InventoryMovementResponse> getAllMovements() {

        return inventoryMovementRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<InventoryMovementResponse> getMovementsByProductId(Long productId) {

        return inventoryMovementRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public InventoryMovementResponse adjustStock(AdjustmentRequest request, String username) {

        if (request.getQuantity() == null || request.getQuantity() == 0) {
            throw new RuntimeException("Adjustment quantity must be non-zero");
        }

        ProductEntity product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        InventoryMovementEntity movement = InventoryMovementEntity.builder()
                .product(product)
                .movementType(MovementType.ADJUSTMENT)
                .quantity(request.getQuantity())
                .notes(request.getReason())
                .performedBy(username != null ? username : "system")
                .build();

        product.setStockQuantity(product.getStockQuantity() + request.getQuantity());

        productRepository.save(product);
        inventoryMovementRepository.save(movement);

        return toResponse(movement);
    }

    private InventoryMovementResponse toResponse(InventoryMovementEntity movement) {

        return InventoryMovementResponse.builder()
                .id(movement.getId())
                .productId(movement.getProduct().getId())
                .productName(movement.getProduct().getName())
                .movementType(movement.getMovementType().name())
                .quantity(movement.getQuantity())
                .referenceId(movement.getReferenceId())
                .referenceType(movement.getReferenceType())
                .notes(movement.getNotes())
                .performedBy(movement.getPerformedBy())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
