package com.devkanyanta.pos.support;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportGuideRepository extends JpaRepository<SupportGuideEntity, Long> {

    List<SupportGuideEntity> findByActiveTrueOrderByTopicAscSortOrderAscIdAsc();

    List<SupportGuideEntity> findByTopicIgnoreCaseAndActiveTrueOrderBySortOrderAscIdAsc(String topic);

    List<SupportGuideEntity> findAllByOrderByTopicAscSortOrderAscIdAsc();
}
