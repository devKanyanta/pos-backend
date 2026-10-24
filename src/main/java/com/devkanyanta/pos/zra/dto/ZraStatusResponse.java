package com.devkanyanta.pos.zra.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ZraStatusResponse {

    private boolean enabled;

    private boolean planAllowsZra;

    private boolean deviceInitialized;

    private long pending;

    private long retrying;

    private long failed;

    private long succeeded;

    private long unmappedProducts;

    private long unregisteredProducts;
}
