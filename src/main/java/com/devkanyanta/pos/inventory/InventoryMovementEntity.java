package com.devkanyanta.pos.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.devkanyanta.pos.product.ProductEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One immutable line of the stock ledger. {@link #quantity} is the signed change, {@link #balanceAfter}
 * the product's on-hand quantity right after it, and {@link #unitCost} the cost the change was valued at
 * (weighted-average cost, or the purchase cost for stock coming in).
 */
@Entity
@Table(name = "inventory_movements", indexes = {
        @Index(name = "idx_movement_product_created", columnList = "product_id, created_at"),
        @Index(name = "idx_movement_type_created", columnList = "movement_type, created_at"),
        @Index(name = "idx_movement_reference", columnList = "reference_type, reference_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType movementType;

    /** Signed: positive adds stock, negative removes it. */
    @Column(nullable = false)
    private Integer quantity;

    private Long referenceId;

    private String referenceType;

    private String notes;

    /** Why the change happened, for adjustments (e.g. DAMAGED, EXPIRED, LOST). */
    @Column(length = 30)
    private String reason;

    /** Cost per unit the change is valued at; null on rows written before costing existed. */
    @Column(precision = 19, scale = 4)
    private BigDecimal unitCost;

    /** On-hand quantity after this movement; null on rows written before this was tracked. */
    private Integer balanceAfter;

    /** Client-supplied key that makes a manual operation safe to retry. */
    @Column(length = 64, unique = true)
    private String requestId;

    @Column(nullable = false)
    private String performedBy;

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Signed value of this movement at its unit cost (null when the cost is unknown). */
    public BigDecimal getValue() {
        return unitCost == null ? null : unitCost.multiply(BigDecimal.valueOf(quantity)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * RESTOCK, RETURN and TRANSFER are kept for rows written by earlier versions: RESTOCK is a receipt
     * without a purchase order, RETURN a customer return. TRANSFER is unused (single location).
     */
    public enum MovementType {
        /** Stock entered when a product is created or imported. */
        OPENING,
        /** Goods received against a purchase order. */
        PURCHASE_RECEIPT,
        /** Goods received without a purchase order (manual restock). */
        RESTOCK,
        SALE,
        /** Stock restored by voiding an unpaid sale. */
        SALE_VOID,
        /** Saleable goods returned by a customer. */
        RETURN,
        /** Goods sent back to a supplier. */
        SUPPLIER_RETURN,
        ADJUSTMENT,
        /** Variance found by a physical stock count. */
        COUNT,
        TRANSFER
    }
}
