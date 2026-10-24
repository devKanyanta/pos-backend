package com.devkanyanta.pos.zra.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InitializeDeviceRequest {

    /** Taxpayer TPIN (10 digits). */
    private String tpin;

    /** Branch id issued by ZRA, "000" for head office. */
    private String bhfId;

    /** Device serial number registered with ZRA. */
    private String deviceSerialNo;
}
