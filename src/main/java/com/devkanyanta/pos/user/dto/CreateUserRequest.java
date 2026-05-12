package com.devkanyanta.pos.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {

    private String firstName;

    private String lastName;

    private String email;

    private String phoneNumber;

    private String username;

    private String password;
}
