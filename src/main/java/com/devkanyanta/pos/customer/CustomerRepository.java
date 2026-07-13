package com.devkanyanta.pos.customer;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByPhone(String phone);

    Optional<CustomerEntity> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}
