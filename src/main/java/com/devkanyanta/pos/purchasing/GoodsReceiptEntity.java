package com.devkanyanta.pos.purchasing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.devkanyanta.pos.supplier.SupplierEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One delivery of goods against a purchase order. Immutable once recorded. */
@Entity
@Table(name = "goods_receipts", indexes = {
        @Index(name = "idx_grn_po", columnList = "purchase_order_id"),
        @Index(name = "idx_grn_received_at", columnList = "received_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodsReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrderEntity purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierEntity supplier;

    /** Idempotency key: submitting the same key again returns this receipt instead of receiving twice. */
    @Column(nullable = false, unique = true, length = 64)
    private String requestId;

    @Column(length = 100)
    private String supplierInvoiceNumber;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private String receivedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<GoodsReceiptLineEntity> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }

    public BigDecimal getTotalCost() {
        return lines.stream().map(GoodsReceiptLineEntity::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
