package com.devkanyanta.pos.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Audit record of a change to a product's selling price or last purchase cost, and who made it. */
@Entity
@Table(name = "product_price_history", indexes = @Index(name = "idx_price_history_product", columnList = "product_id, changed_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPriceHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(precision = 19, scale = 2)
    private BigDecimal oldPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal newPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal oldCostPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal newCostPrice;

    @Column(nullable = false)
    private String changedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        changedAt = LocalDateTime.now();
    }
}
