package com.devkanyanta.pos.zra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ZraDeviceRepository extends JpaRepository<ZraDeviceEntity, Long> {

    Optional<ZraDeviceEntity> findFirstByActiveTrueOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ZraDeviceEntity d WHERE d.id = :id")
    Optional<ZraDeviceEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
