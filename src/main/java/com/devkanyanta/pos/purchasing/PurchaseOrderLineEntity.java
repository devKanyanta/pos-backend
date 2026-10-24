package com.devkanyanta.pos.purchasing;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.devkanyanta.pos.product.ProductEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "purchase_order_lines", uniqueConstraints =
        @UniqueConstraint(name = "uk_po_line_product", columnNames = {"purchase_order_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrderEntity purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(nullable = false)
    private Integer orderedQuantity;

    /** Total delivered so far across all receipts. Never exceeds {@link #orderedQuantity}. */
    @Column(nullable = false)
    @Builder.Default
    private Integer receivedQuantity = 0;

    /** Units of the delivered goods sent back to the supplier. Informational: it does not reopen the order. */
    @Column(nullable = false)
    @Builder.Default
    private Integer returnedQuantity = 0;

    /** Agreed cost per unit (the receipt may record a different invoiced cost). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    public int getOutstandingQuantity() {
        return orderedQuantity - receivedQuantity;
    }

    public BigDecimal getOrderedCost() {
        return unitCost.multiply(BigDecimal.valueOf(orderedQuantity)).setScale(2, RoundingMode.HALF_UP);
    }
}
