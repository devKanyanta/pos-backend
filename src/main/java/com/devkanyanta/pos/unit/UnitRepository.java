package com.devkanyanta.pos.unit;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<UnitEntity, Long> {

    Optional<UnitEntity> findByCode(String code);
}
