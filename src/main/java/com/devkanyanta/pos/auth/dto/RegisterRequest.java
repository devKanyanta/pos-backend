package com.devkanyanta.pos.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    private String firstName;

    private String lastName;

    private String email;

    private String phoneNumber;

    private String username;

    private String password;
}