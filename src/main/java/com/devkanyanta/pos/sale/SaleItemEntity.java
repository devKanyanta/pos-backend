package com.devkanyanta.pos.sale;

import java.math.BigDecimal;

import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.zra.ZraTaxType;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sale_items", indexes = @jakarta.persistence.Index(name = "idx_sale_item_product", columnList = "product_id"))
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

    /**
     * Average cost per unit when the sale was made. Fixed at sale time so the cost of goods sold never
     * changes when later purchases move the product's average. Null for lines without a known product.
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private Integer quantity;

    /** unitPrice x quantity. */
    @Column(precision = 19, scale = 2)
    private BigDecimal grossAmount;

    /** grossAmount - line discount (VAT exclusive). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(precision = 5, scale = 2)
    private BigDecimal vatRate;

    /** Share of the sale-level discount allocated to this line. */
    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal saleDiscountShare = BigDecimal.ZERO;

    /** lineTotal - saleDiscountShare: the amount VAT is charged on. */
    @Column(precision = 19, scale = 2)
    private BigDecimal taxableAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal vatAmount;

    /** Line-level discount. */
    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ItemStatus itemStatus = ItemStatus.ACTIVE;

    @Builder.Default
    private Integer refundedQuantity = 0;

    /** Value (VAT inclusive) refunded so far. */
    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal refundedVatAmount = BigDecimal.ZERO;

    // ---- ZRA snapshot at time of sale ---------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ZraTaxType zraTaxType;

    @Column(length = 40)
    private String zraItemCode;

    @Column(length = 20)
    private String zraItemClassCode;

    @Column(length = 10)
    private String zraPackagingUnitCode;

    @Column(length = 10)
    private String zraQuantityUnitCode;

    /** Line value including VAT (taxable amount + VAT). */
    public BigDecimal getTotalInclVat() {
        BigDecimal taxable = taxableAmount != null ? taxableAmount : lineTotal;
        BigDecimal vat = vatAmount != null ? vatAmount : BigDecimal.ZERO;
        return taxable.add(vat);
    }

    public int getRemainingQuantity() {
        return quantity - (refundedQuantity != null ? refundedQuantity : 0);
    }

    public enum ItemStatus {
        ACTIVE, REFUNDED
    }
}
