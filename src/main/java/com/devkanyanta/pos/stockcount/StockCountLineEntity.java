package com.devkanyanta.pos.stockcount;

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
@Table(name = "stock_count_lines", uniqueConstraints =
        @UniqueConstraint(name = "uk_count_line_product", columnNames = {"stock_count_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_count_id", nullable = false)
    private StockCountEntity stockCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    /** System quantity when the line was added; the count is measured against it. */
    @Column(nullable = false)
    private Integer expectedQuantity;

    /** What was physically counted; null until counted. */
    private Integer countedQuantity;

    /** counted - expected, fixed at commit. */
    private Integer variance;

    /** Average cost the variance was valued at, fixed at commit. */
    @Column(precision = 19, scale = 4)
    private BigDecimal unitCost;

    public BigDecimal getVarianceValue() {
        return variance == null || unitCost == null ? null
                : unitCost.multiply(BigDecimal.valueOf(variance)).setScale(2, RoundingMode.HALF_UP);
    }
}
