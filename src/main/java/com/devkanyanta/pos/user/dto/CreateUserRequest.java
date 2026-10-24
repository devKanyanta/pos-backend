package com.devkanyanta.pos.user.dto;

import java.util.Set;

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

    /** Role ids to assign; defaults to CASHIER when omitted. */
    private Set<Long> roleIds;
}
