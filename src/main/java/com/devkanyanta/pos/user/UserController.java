package com.devkanyanta.pos.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.user.dto.CreateUserRequest;
import com.devkanyanta.pos.user.dto.UpdateUserRequest;
import com.devkanyanta.pos.user.dto.UserResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_CREATE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(
            @RequestBody CreateUserRequest request
    ) {
        return userService.createUser(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_VIEW + "')")
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_VIEW + "')")
    public UserResponse getUserById(
            @PathVariable Long id
    ) {
        return userService.getUserById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_UPDATE + "')")
    public UserResponse updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request
    ) {
        return userService.updateUser(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_DELETE + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(
            @PathVariable Long id
    ) {
        userService.deleteUser(id);
    }
}
