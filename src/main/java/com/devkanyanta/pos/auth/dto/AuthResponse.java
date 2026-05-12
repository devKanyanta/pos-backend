package com.devkanyanta.pos.auth.dto;

import java.util.Set;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private String token;

    private String username;

    private Set<String> roles;
}