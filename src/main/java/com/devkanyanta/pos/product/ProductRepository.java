package com.devkanyanta.pos.product;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository
        extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByBarcode(String barcode);

    Optional<ProductEntity> findBySku(String sku);

    boolean existsByCategoryId(Long categoryId);
}
