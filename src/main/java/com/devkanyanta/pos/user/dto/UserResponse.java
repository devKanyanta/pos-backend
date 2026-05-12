package com.devkanyanta.pos.user.dto;

import java.time.LocalDateTime;
import java.util.Set;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {

    private Long id;

    private String firstName;

    private String lastName;

    private String email;

    private String phoneNumber;

    private String username;

    private Set<String> roles;

    private LocalDateTime createdAt;
}
