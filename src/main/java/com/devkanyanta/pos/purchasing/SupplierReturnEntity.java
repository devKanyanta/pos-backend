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

/** Goods sent back to a supplier. Removes stock; the original receipt and purchase order are untouched. */
@Entity
@Table(name = "supplier_returns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierReturnEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierEntity supplier;

    /** The order the goods came from, when known. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrderEntity purchaseOrder;

    @Column(nullable = false, unique = true, length = 64)
    private String requestId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private String returnedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime returnedAt;

    @OneToMany(mappedBy = "supplierReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<SupplierReturnLineEntity> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (returnedAt == null) {
            returnedAt = LocalDateTime.now();
        }
    }

    public BigDecimal getCreditTotal() {
        return lines.stream().map(SupplierReturnLineEntity::getCreditTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
