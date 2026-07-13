package com.devkanyanta.pos.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity, Long> {

    Optional<PaymentMethodEntity> findByName(String name);

    Optional<PaymentMethodEntity> findByCode(String code);

    boolean existsByName(String name);

    boolean existsByCode(String code);
}
