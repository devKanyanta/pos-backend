package com.devkanyanta.pos.sync.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HeartbeatRequest {

    private String deviceId;

    /** Transactions waiting in the device's local queue. */
    private Integer pendingQueueSize;

    /** Connectivity state as seen by the device. */
    private Boolean online;

    private String appVersion;
}
