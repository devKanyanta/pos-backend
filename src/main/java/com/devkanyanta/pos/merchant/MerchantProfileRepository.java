package com.devkanyanta.pos.merchant;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantProfileRepository extends JpaRepository<MerchantProfileEntity, Long> {

    Optional<MerchantProfileEntity> findFirstByOrderByIdAsc();
}
