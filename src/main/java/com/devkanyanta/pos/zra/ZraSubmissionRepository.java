package com.devkanyanta.pos.zra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ZraSubmissionRepository extends JpaRepository<ZraSubmissionEntity, Long> {

    List<ZraSubmissionEntity> findAllByOrderByCreatedAtDesc();
}
