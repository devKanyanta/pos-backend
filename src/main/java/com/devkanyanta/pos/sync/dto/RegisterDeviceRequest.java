package com.devkanyanta.pos.sync.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDeviceRequest {

    private String deviceId;

    private String name;

    private String model;

    private String appVersion;
}
