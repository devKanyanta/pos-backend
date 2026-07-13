package com.devkanyanta.pos.branch.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BranchResponse {

    private Long id;

    private String name;

    private String zraTpin;

    private String phone;

    private String email;

    private String address;

    private Boolean isActive;

    private LocalDateTime createdAt;
}
