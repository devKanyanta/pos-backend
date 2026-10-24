package com.devkanyanta.pos.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.shared.exception.BadRequestException;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

/**
 * Single place where product stock changes. Every change updates the stock snapshot on the product,
 * keeps its weighted-average cost current and appends an immutable {@link InventoryMovementEntity}.
 * Callers must hold a transaction and should load the product with
 * {@link ProductRepository#findByIdForUpdate(Long)} to serialise concurrent updates.
 *
 * <h3>Rules enforced here</h3>
 * <ul>
 *   <li><b>No negative stock</b> unless the caller sets {@code allowNegative}. Only offline-synced sales do
 *       (the goods already left the shop); everything else is refused with a 400.</li>
 *   <li><b>Weighted-average cost.</b> Stock coming in with an explicit unit cost re-averages:
 *       {@code (onHand x avg + qty x cost) / (onHand + qty)}, treating a negative balance as zero. Stock coming in
 *       without a cost (adjustments, counts) and every outflow is valued at the current average, which an
 *       outflow never changes.</li>
 *   <li>Each movement records the unit cost it was valued at and the balance after it.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class StockLedger {

    private static final int COST_SCALE = 4;

    private final ProductRepository productRepository;

    private final InventoryMovementRepository inventoryMovementRepository;

    /** Describes one stock change. */
    @Builder
    public record Change(
            ProductEntity product,
            MovementType type,
            int quantity,
            /** Cost per unit for stock coming in; null = value at the current average. */
            BigDecimal unitCost,
            Long referenceId,
            String referenceType,
            String reason,
            String notes,
            String performedBy,
            boolean allowNegative,
            String requestId
    ) {
    }

    public InventoryMovementEntity record(Change change) {

        ProductEntity product = change.product();
        int current = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        int updated = current + change.quantity();

        if (change.quantity() == 0) {
            throw new BadRequestException("Stock change must be non-zero");
        }
        if (updated < 0 && !change.allowNegative()) {
            throw new BadRequestException("Insufficient stock for product: " + product.getName()
                    + ". Available: " + current + ", requested: " + (-change.quantity()));
        }

        BigDecimal average = product.getAverageCost() != null ? product.getAverageCost() : BigDecimal.ZERO;
        BigDecimal movementCost = average;

        if (change.quantity() > 0 && change.unitCost() != null) {
            if (change.unitCost().signum() < 0) {
                throw new BadRequestException("Unit cost cannot be negative");
            }
            movementCost = change.unitCost().setScale(COST_SCALE, RoundingMode.HALF_UP);
            BigDecimal base = BigDecimal.valueOf(Math.max(current, 0));
            BigDecimal incoming = BigDecimal.valueOf(change.quantity());
            BigDecimal newAverage = base.multiply(average).add(incoming.multiply(movementCost))
                    .divide(base.add(incoming), COST_SCALE, RoundingMode.HALF_UP);
            product.setAverageCost(newAverage);
        } else if (product.getAverageCost() == null) {
            product.setAverageCost(average.setScale(COST_SCALE, RoundingMode.HALF_UP));
        }

        product.setStockQuantity(updated);
        productRepository.save(product);

        String notes = change.notes();
        return inventoryMovementRepository.save(InventoryMovementEntity.builder()
                .product(product)
                .movementType(change.type())
                .quantity(change.quantity())
                .unitCost(movementCost.setScale(COST_SCALE, RoundingMode.HALF_UP))
                .balanceAfter(updated)
                .referenceId(change.referenceId())
                .referenceType(change.referenceType())
                .reason(change.reason())
                .notes(notes != null && notes.length() > 255 ? notes.substring(0, 255) : notes)
                .requestId(change.requestId())
                .performedBy(change.performedBy() != null ? change.performedBy() : "system")
                .build());
    }

    /** Convenience for the common case: value at the current average, negative stock refused. */
    public InventoryMovementEntity record(
            ProductEntity product,
            MovementType type,
            int quantityChange,
            Long referenceId,
            String referenceType,
            String notes,
            String performedBy
    ) {
        return record(Change.builder()
                .product(product).type(type).quantity(quantityChange)
                .referenceId(referenceId).referenceType(referenceType)
                .notes(notes).performedBy(performedBy)
                .build());
    }
}
