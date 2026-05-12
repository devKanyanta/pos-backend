package com.devkanyanta.pos.user.mapper;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.devkanyanta.pos.user.UserEntity;
import com.devkanyanta.pos.user.dto.UserResponse;

@Component
public class UserMapper {

    public UserResponse toResponse(UserEntity user) {

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .username(user.getUsername())
                .roles(
                        user.getRoles()
                                .stream()
                                .map(role -> role.getName())
                                .collect(Collectors.toSet())
                )
                .createdAt(user.getCreatedAt())
                .build();
    }
}