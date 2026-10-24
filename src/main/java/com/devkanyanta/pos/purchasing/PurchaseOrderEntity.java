package com.devkanyanta.pos.purchasing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.devkanyanta.pos.shared.BaseEntity;
import com.devkanyanta.pos.supplier.SupplierEntity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An order placed with a supplier. Ordering does not change stock; only goods receipts do.
 *
 * <pre>
 * DRAFT -> ORDERED -> PARTIALLY_RECEIVED -> RECEIVED
 *   |         |              |
 *   +---------+-> CANCELLED  +-> CLOSED (short delivery accepted)
 * </pre>
 */
@Entity
@Table(name = "purchase_orders", indexes = {
        @Index(name = "idx_po_supplier", columnList = "supplier_id"),
        @Index(name = "idx_po_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierEntity supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    private LocalDate orderDate;

    private LocalDate expectedDate;

    /** The supplier's own reference for this order. */
    @Column(length = 100)
    private String supplierReference;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private String createdBy;

    private String orderedBy;

    private LocalDateTime orderedAt;

    private String cancelledBy;

    private LocalDateTime cancelledAt;

    @Column(length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PurchaseOrderLineEntity> lines = new ArrayList<>();

    public BigDecimal getTotalCost() {
        return lines.stream().map(PurchaseOrderLineEntity::getOrderedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public boolean hasReceipts() {
        return lines.stream().anyMatch(l -> l.getReceivedQuantity() > 0);
    }

    public enum Status {
        DRAFT, ORDERED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED, CLOSED
    }
}
