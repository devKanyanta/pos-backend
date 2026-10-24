package com.devkanyanta.pos.purchasing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLineEntity, Long> {

    boolean existsByProductId(Long productId);
}
