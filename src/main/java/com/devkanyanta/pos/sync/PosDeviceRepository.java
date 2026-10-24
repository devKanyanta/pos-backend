package com.devkanyanta.pos.sync;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PosDeviceRepository extends JpaRepository<PosDeviceEntity, Long> {

    Optional<PosDeviceEntity> findByDeviceId(String deviceId);

    List<PosDeviceEntity> findAllByOrderByLastSeenAtDesc();
}
