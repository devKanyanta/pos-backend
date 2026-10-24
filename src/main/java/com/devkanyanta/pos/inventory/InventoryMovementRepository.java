package com.devkanyanta.pos.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovementEntity, Long> {

    List<InventoryMovementEntity> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<InventoryMovementEntity> findAllByOrderByCreatedAtDesc();

    boolean existsByProductId(Long productId);
}
