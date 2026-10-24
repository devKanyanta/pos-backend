package com.devkanyanta.pos.sync;

import java.time.LocalDateTime;

import com.devkanyanta.pos.shared.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

/** An Android till (or other client) that records sales offline and syncs them. */
@Entity
@Table(name = "pos_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosDeviceEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Client-generated stable identifier (e.g. a UUID stored on the device). */
    @Column(nullable = false, unique = true, length = 64)
    private String deviceId;

    private String name;

    private String model;

    @Column(length = 40)
    private String appVersion;

    private String registeredBy;

    /** Last user that synced from this device. */
    private String lastUser;

    @Builder.Default
    private Boolean active = true;

    private LocalDateTime lastSeenAt;

    private LocalDateTime lastSyncAt;

    /** Number of transactions waiting in the device's local queue at the last heartbeat/sync. */
    @Builder.Default
    private Integer pendingQueueSize = 0;

    /** Whether the device reported itself online at its last heartbeat. */
    private Boolean reportedOnline;
}
