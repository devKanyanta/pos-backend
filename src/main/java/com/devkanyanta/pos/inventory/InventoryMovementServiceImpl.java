package com.devkanyanta.pos.inventory;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.inventory.dto.AdjustmentRequest;
import com.devkanyanta.pos.inventory.dto.InventoryMovementResponse;
import com.devkanyanta.pos.inventory.dto.RestockRequest;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryMovementServiceImpl implements InventoryMovementService {

    private final InventoryMovementRepository inventoryMovementRepository;

    private final ProductRepository productRepository;

    private final StockLedger stockLedger;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> getAllMovements() {

        return inventoryMovementRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> getMovementsByProductId(Long productId) {

        if (!productRepository.existsById(productId)) {
            throw new NotFoundException("Product not found");
        }

        return inventoryMovementRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public InventoryMovementResponse adjustStock(AdjustmentRequest request, String username) {

        if (request == null || request.getProductId() == null) {
            throw new BadRequestException("Product id is required");
        }

        if (request.getQuantity() == null || request.getQuantity() == 0) {
            throw new BadRequestException("Adjustment quantity must be non-zero");
        }

        if (!StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("A reason is required for stock adjustments");
        }

        ProductEntity product = lockProduct(request.getProductId());

        if (!Boolean.TRUE.equals(product.getTrackInventory())) {
            throw new BadRequestException("Stock is not tracked for product: " + product.getName());
        }

        int current = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        if (current + request.getQuantity() < 0) {
            throw new BadRequestException("Adjustment would make stock negative. Current stock: " + current);
        }

        InventoryMovementEntity movement = stockLedger.record(product, MovementType.ADJUSTMENT,
                request.getQuantity(), null, "MANUAL_ADJUSTMENT", request.getReason().trim(), username);

        return toResponse(movement);
    }

    @Override
    @Transactional
    public InventoryMovementResponse restock(RestockRequest request, String username) {

        if (request == null || request.getProductId() == null) {
            throw new BadRequestException("Product id is required");
        }

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BadRequestException("Restock quantity must be positive");
        }

        if (request.getUnitCost() != null && request.getUnitCost().signum() < 0) {
            throw new BadRequestException("Unit cost cannot be negative");
        }

        ProductEntity product = lockProduct(request.getProductId());

        if (!Boolean.TRUE.equals(product.getTrackInventory())) {
            throw new BadRequestException("Stock is not tracked for product: " + product.getName());
        }

        if (request.getUnitCost() != null) {
            product.setCostPrice(request.getUnitCost());
        }

        String notes = StringUtils.hasText(request.getNotes()) ? request.getNotes().trim() : "Stock received";

        InventoryMovementEntity movement = stockLedger.record(product, MovementType.RESTOCK,
                request.getQuantity(), null, "RESTOCK", notes, username);

        return toResponse(movement);
    }

    private ProductEntity lockProduct(Long productId) {

        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
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
