package com.devkanyanta.pos.auth.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.devkanyanta.pos.user.role.RoleEntity;
import com.devkanyanta.pos.user.role.RoleRepository;
import com.devkanyanta.pos.user.role_permissions.PermissionEntity;
import com.devkanyanta.pos.user.role_permissions.PermissionRepository;

@Configuration
public class AuthorizationDataSeeder {

    @Bean
    public CommandLineRunner seedAuthorizationData(
            PermissionRepository permissionRepository,
            RoleRepository roleRepository
    ) {
        return args -> {
            List<String> permissionNames = List.of(
                    PermissionConstants.PRODUCT_VIEW,
                    PermissionConstants.PRODUCT_CREATE,
                    PermissionConstants.PRODUCT_UPDATE,
                    PermissionConstants.PRODUCT_DELETE,
                    PermissionConstants.CATEGORY_VIEW,
                    PermissionConstants.CATEGORY_CREATE,
                    PermissionConstants.CATEGORY_UPDATE,
                    PermissionConstants.CATEGORY_DELETE
            );

            permissionNames.forEach(permissionName -> permissionRepository.findByName(permissionName)
                    .orElseGet(() -> permissionRepository.save(
                            PermissionEntity.builder()
                                    .name(permissionName)
                                    .build()
                    )));

            RoleEntity adminRole = getOrCreateRole(roleRepository, "ADMIN");
            RoleEntity managerRole = getOrCreateRole(roleRepository, "MANAGER");
            RoleEntity cashierRole = getOrCreateRole(roleRepository, "CASHIER");

            assignPermissions(adminRole, permissionNames, permissionRepository, roleRepository);
            assignPermissions(
                    managerRole,
                    List.of(
                            PermissionConstants.PRODUCT_VIEW,
                            PermissionConstants.PRODUCT_CREATE,
                            PermissionConstants.PRODUCT_UPDATE,
                            PermissionConstants.CATEGORY_VIEW
                    ),
                    permissionRepository,
                    roleRepository
            );
            assignPermissions(
                    cashierRole,
                    List.of(
                            PermissionConstants.PRODUCT_VIEW,
                            PermissionConstants.CATEGORY_VIEW
                    ),
                    permissionRepository,
                    roleRepository
            );
        };
    }

    private RoleEntity getOrCreateRole(RoleRepository roleRepository, String roleName) {

        return roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(
                        RoleEntity.builder()
                                .name(roleName)
                                .build()
                ));
    }

    private void assignPermissions(
            RoleEntity role,
            List<String> permissionNames,
            PermissionRepository permissionRepository,
            RoleRepository roleRepository
    ) {
        Set<PermissionEntity> permissions = new HashSet<>();

        permissionNames.forEach(permissionName -> permissions.add(
                permissionRepository.findByName(permissionName)
                        .orElseThrow(() -> new RuntimeException("Permission not found: " + permissionName))
        ));

        role.setPermissions(permissions);
        roleRepository.save(role);
    }
}
