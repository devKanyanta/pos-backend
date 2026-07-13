package com.devkanyanta.pos.customer.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerResponse {

    private Long id;

    private String firstName;

    private String lastName;

    private String phone;

    private String email;

    private String tpin;

    private String address;

    private Boolean isActive;

    private LocalDateTime createdAt;
}
