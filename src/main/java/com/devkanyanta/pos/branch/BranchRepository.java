package com.devkanyanta.pos.branch;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<BranchEntity, Long> {

    Optional<BranchEntity> findByName(String name);

    boolean existsByName(String name);
}
