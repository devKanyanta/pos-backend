package com.devkanyanta.pos.product;

import java.math.BigDecimal;

import com.devkanyanta.pos.category.CategoryEntity;
import com.devkanyanta.pos.shared.BaseEntity;

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

    @Column(nullable = false)
    private Integer stockQuantity;

    private Boolean active;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private CategoryEntity category;
}