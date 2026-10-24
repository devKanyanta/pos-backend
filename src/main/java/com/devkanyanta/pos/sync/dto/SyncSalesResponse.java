package com.devkanyanta.pos.sync.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SyncSalesResponse {

    private LocalDateTime serverTime;

    private int created;

    private int duplicates;

    private int rejected;

    private List<SyncSaleResult> results;
}
