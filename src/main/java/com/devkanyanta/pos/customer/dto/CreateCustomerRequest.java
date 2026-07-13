package com.devkanyanta.pos.customer.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCustomerRequest {

    private String firstName;

    private String lastName;

    private String phone;

    private String email;

    private String tpin;

    private String address;
}
