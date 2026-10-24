package com.devkanyanta.pos.reconciliation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRepository extends JpaRepository<ReconciliationEntity, Long> {

    Optional<ReconciliationEntity> findByBusinessDate(LocalDate businessDate);

    List<ReconciliationEntity> findByBusinessDateBetweenOrderByBusinessDateDesc(LocalDate from, LocalDate to);
}
