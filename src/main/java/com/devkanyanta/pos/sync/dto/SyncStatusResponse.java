package com.devkanyanta.pos.sync.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SyncStatusResponse {

    private LocalDateTime serverTime;

    private String deviceId;

    private boolean deviceRegistered;

    private boolean deviceActive;

    private LocalDateTime lastSyncAt;

    private Integer pendingQueueSize;

    private long zraPending;

    private long zraFailed;

    private long salesNeedingReview;
}
