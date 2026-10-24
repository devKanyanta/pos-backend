package com.devkanyanta.pos.user;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.ConflictException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.user.dto.CreateUserRequest;
import com.devkanyanta.pos.user.dto.UpdateUserRequest;
import com.devkanyanta.pos.user.dto.UserResponse;
import com.devkanyanta.pos.user.mapper.UserMapper;
import com.devkanyanta.pos.user.role.RoleEntity;
import com.devkanyanta.pos.user.role.RoleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final RoleRepository roleRepository;

    private final MerchantProfileService merchantProfileService;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {

        if (request == null
                || !StringUtils.hasText(request.getUsername())
                || !StringUtils.hasText(request.getEmail())
                || !StringUtils.hasText(request.getPassword())) {
            throw new BadRequestException("Username, email and password are required");
        }

        if (request.getPassword().length() < 6) {
            throw new BadRequestException("Password must be at least 6 characters");
        }

        if (userRepository.count() > 0) {
            merchantProfileService.requireFeature(MerchantProfileService.FEATURE_MULTI_USER, "Additional users");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username already exists");
        }

        Set<RoleEntity> roles = new HashSet<>();

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            roles.addAll(resolveRoles(request.getRoleIds()));
        } else {
            roles.add(roleRepository.findByName("CASHIER")
                    .orElseThrow(() -> new NotFoundException("Default role not found: CASHIER")));
        }

        UserEntity user = UserEntity.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(roles)
                .build();

        UserEntity savedUser = userRepository.save(user);

        return userMapper.toResponse(savedUser);
    }

    @Override
    public List<UserResponse> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    public UserResponse getUserById(Long id) {

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse updateUser(Long id, UpdateUserRequest request) {

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (StringUtils.hasText(request.getFirstName())) {
            user.setFirstName(request.getFirstName());
        }

        if (StringUtils.hasText(request.getLastName())) {
            user.setLastName(request.getLastName());
        }

        if (StringUtils.hasText(request.getEmail())
                && !request.getEmail().equals(user.getEmail())) {

            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email already exists");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        if (StringUtils.hasText(request.getUsername())
                && !request.getUsername().equals(user.getUsername())) {

            if (userRepository.existsByUsername(request.getUsername())) {
                throw new ConflictException("Username already exists");
            }
            user.setUsername(request.getUsername());
        }

        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getRoleIds() != null) {
            if (request.getRoleIds().isEmpty()) {
                throw new BadRequestException("A user must have at least one role");
            }
            user.setRoles(resolveRoles(request.getRoleIds()));
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public void deleteUser(Long id) {

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        userRepository.delete(user);
    }

    private Set<RoleEntity> resolveRoles(Set<Long> roleIds) {

        Set<RoleEntity> roles = new HashSet<>();
        roleIds.forEach(roleId -> roles.add(roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found: " + roleId))));
        return roles;
    }
}
