package com.devkanyanta.pos.sync.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SyncSalesRequest {

    private String deviceId;

    /** Items still queued on the device after this batch (for the dashboard). */
    private Integer pendingQueueSize;

    private List<OfflineSaleRequest> sales;
}
