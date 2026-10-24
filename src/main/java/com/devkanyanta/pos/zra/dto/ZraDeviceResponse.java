package com.devkanyanta.pos.zra.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ZraDeviceResponse {

    private Long id;

    private String tpin;

    private String bhfId;

    private String deviceSerialNo;

    private String sdcId;

    private String mrcNo;

    private String taxpayerName;

    private String branchName;

    private Long lastInvoiceNo;

    private LocalDateTime initializedAt;

    private boolean integrationEnabled;

    private String vsdcBaseUrl;
}
