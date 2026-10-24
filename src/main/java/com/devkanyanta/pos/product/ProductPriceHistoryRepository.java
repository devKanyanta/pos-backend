package com.devkanyanta.pos.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPriceHistoryRepository extends JpaRepository<ProductPriceHistoryEntity, Long> {

    List<ProductPriceHistoryEntity> findByProductIdOrderByChangedAtDescIdDesc(Long productId);
}
