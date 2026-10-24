package com.devkanyanta.pos.sync.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeviceResponse {

    private Long id;

    private String deviceId;

    private String name;

    private String model;

    private String appVersion;

    private Boolean active;

    private String registeredBy;

    private String lastUser;

    private LocalDateTime lastSeenAt;

    private LocalDateTime lastSyncAt;

    private Integer pendingQueueSize;

    private Boolean reportedOnline;

    private LocalDateTime createdAt;
}
