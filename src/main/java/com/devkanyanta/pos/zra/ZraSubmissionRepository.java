package com.devkanyanta.pos.zra;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionStatus;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionType;

import jakarta.persistence.LockModeType;

public interface ZraSubmissionRepository extends JpaRepository<ZraSubmissionEntity, Long> {

    List<ZraSubmissionEntity> findAllByOrderByCreatedAtDesc();

    List<ZraSubmissionEntity> findBySubmissionStatusOrderByCreatedAtDesc(SubmissionStatus status);

    Optional<ZraSubmissionEntity> findFirstBySaleIdAndSubmissionTypeOrderByIdDesc(Long saleId, SubmissionType type);

    Optional<ZraSubmissionEntity> findFirstByRefundIdOrderByIdDesc(Long refundId);

    @Query("SELECT z.id FROM ZraSubmissionEntity z WHERE z.submissionStatus IN :statuses "
            + "AND z.nextAttemptAt <= :now ORDER BY z.id ASC")
    List<Long> findDueIds(@Param("statuses") Collection<SubmissionStatus> statuses,
                          @Param("now") LocalDateTime now,
                          Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT z FROM ZraSubmissionEntity z WHERE z.id = :id")
    Optional<ZraSubmissionEntity> findByIdForUpdate(@Param("id") Long id);

    long countBySubmissionStatus(SubmissionStatus status);

    long countBySubmissionStatusIn(Collection<SubmissionStatus> statuses);
}
