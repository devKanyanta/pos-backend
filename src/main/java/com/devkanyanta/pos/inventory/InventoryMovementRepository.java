package com.devkanyanta.pos.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovementEntity, Long>, JpaSpecificationExecutor<InventoryMovementEntity> {

    List<InventoryMovementEntity> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<InventoryMovementEntity> findAllByOrderByCreatedAtDesc();

    boolean existsByProductId(Long productId);

    java.util.Optional<InventoryMovementEntity> findByRequestId(String requestId);
}
