package com.devkanyanta.pos.sale.refund;

import java.math.BigDecimal;

import com.devkanyanta.pos.sale.SaleItemEntity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sale_refund_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleRefundItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    private SaleRefundEntity refund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_id", nullable = false)
    private SaleItemEntity saleItem;

    @Column(nullable = false)
    private Integer quantity;

    /** Taxable (VAT exclusive) value refunded. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal taxableAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal vatAmount;

    /** taxableAmount + vatAmount. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;
}
