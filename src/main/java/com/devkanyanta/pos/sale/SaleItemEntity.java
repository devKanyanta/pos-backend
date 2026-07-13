package com.devkanyanta.pos.sale;

import java.math.BigDecimal;

import com.devkanyanta.pos.product.ProductEntity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private SaleEntity sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private ProductEntity product;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(precision = 19, scale = 2)
    private BigDecimal vatAmount;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ItemStatus itemStatus = ItemStatus.ACTIVE;

    @Builder.Default
    private Integer refundedQuantity = 0;

    public enum ItemStatus {
        ACTIVE, REFUNDED
    }
}
