package com.devkanyanta.pos.receipt;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptRepository extends JpaRepository<ReceiptEntity, Long> {

    Optional<ReceiptEntity> findBySaleId(Long saleId);
}
