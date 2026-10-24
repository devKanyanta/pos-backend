package com.devkanyanta.pos.support.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupportGuideRequest {

    private String title;

    private String topic;

    private String description;

    private String mediaUrl;

    private Integer sortOrder;

    private Boolean active;
}
