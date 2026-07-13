package com.devkanyanta.pos.customer.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCustomerRequest {

    private String firstName;

    private String lastName;

    private String phone;

    private String email;

    private String tpin;

    private String address;

    private Boolean isActive;
}
