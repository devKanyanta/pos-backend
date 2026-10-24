package com.devkanyanta.pos.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.devkanyanta.pos.category.CategoryEntity;
import com.devkanyanta.pos.shared.BaseEntity;
import com.devkanyanta.pos.zra.ZraTaxType;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String barcode;

    @Column(unique = true)
    private String sku;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(precision = 19, scale = 2)
    private BigDecimal costPrice;

    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal vatRate = new BigDecimal("16.00");

    @Builder.Default
    private Boolean trackInventory = true;

    @Column(nullable = false)
    private Integer stockQuantity;

    /** Low-stock alert level; when null the global pos.low-stock-threshold applies. */
    private Integer reorderLevel;

    private Boolean active;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private CategoryEntity category;

    // ---- ZRA Smart Invoice item mapping -------------------------------------------------

    /** VAT category; determines {@link #vatRate}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ZraTaxType zraTaxType;

    /** ZRA item code (itemCd). Generated on registration when not supplied. */
    @Column(unique = true, length = 40)
    private String zraItemCode;

    /** ZRA item classification code (itemClsCd, UNSPSC based). */
    @Column(length = 20)
    private String zraItemClassCode;

    /** 1 = raw material, 2 = finished product, 3 = service. */
    @Column(length = 2)
    private String zraItemTypeCode;

    /** Country of origin (ISO alpha-2). */
    @Column(length = 2)
    private String zraOriginCountryCode;

    /** Packaging unit code (pkgUnitCd). */
    @Column(length = 10)
    private String zraPackagingUnitCode;

    /** Quantity unit of measure code (qtyUnitCd). */
    @Column(length = 10)
    private String zraQuantityUnitCode;

    /** Whether the item has been registered with the VSDC (items/saveItem). */
    @Builder.Default
    private Boolean zraRegistered = false;

    private LocalDateTime zraRegisteredAt;

    /** True when the product carries every field a compliant Smart Invoice needs. */
    public boolean isZraMapped() {
        return zraTaxType != null
                && zraItemClassCode != null && !zraItemClassCode.isBlank()
                && zraItemTypeCode != null && !zraItemTypeCode.isBlank()
                && zraQuantityUnitCode != null && !zraQuantityUnitCode.isBlank()
                && zraPackagingUnitCode != null && !zraPackagingUnitCode.isBlank();
    }
}
