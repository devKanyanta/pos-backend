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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "supplier_return_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierReturnLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_return_id", nullable = false)
    private SupplierReturnEntity supplierReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_line_id")
    private PurchaseOrderLineEntity poLine;

    @Column(nullable = false)
    private Integer quantity;

    /** Credit the supplier gives per unit (informational; stock leaves at the average cost). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal creditUnitCost;

    /** Average cost the units left stock at. */
    @Column(precision = 19, scale = 4)
    private BigDecimal stockUnitCost;

    public BigDecimal getCreditTotal() {
        return creditUnitCost.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}
