package com.devkanyanta.pos.user;

import java.util.List;

import com.devkanyanta.pos.user.dto.CreateUserRequest;
import com.devkanyanta.pos.user.dto.UserResponse;

public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    List<UserResponse> getAllUsers();

    UserResponse getUserById(Long id);
}
