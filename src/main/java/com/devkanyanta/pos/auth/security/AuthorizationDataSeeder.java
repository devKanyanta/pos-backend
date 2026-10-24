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
                    PermissionConstants.CATEGORY_DELETE,
                    PermissionConstants.USER_VIEW,
                    PermissionConstants.USER_CREATE,
                    PermissionConstants.USER_UPDATE,
                    PermissionConstants.USER_DELETE,
                    PermissionConstants.BRANCH_VIEW,
                    PermissionConstants.BRANCH_CREATE,
                    PermissionConstants.BRANCH_UPDATE,
                    PermissionConstants.BRANCH_DELETE,
                    PermissionConstants.CUSTOMER_VIEW,
                    PermissionConstants.CUSTOMER_CREATE,
                    PermissionConstants.CUSTOMER_UPDATE,
                    PermissionConstants.CUSTOMER_DELETE,
                    PermissionConstants.INVENTORY_VIEW,
                    PermissionConstants.INVENTORY_ADJUST,
                    PermissionConstants.SALE_VIEW,
                    PermissionConstants.SALE_CREATE,
                    PermissionConstants.SALE_VOID,
                    PermissionConstants.SALE_REFUND,
                    PermissionConstants.PAYMENT_METHOD_VIEW,
                    PermissionConstants.PAYMENT_METHOD_CREATE,
                    PermissionConstants.PAYMENT_METHOD_UPDATE,
                    PermissionConstants.PAYMENT_METHOD_DELETE,
                    PermissionConstants.RECEIPT_VIEW,
                    PermissionConstants.RECEIPT_REPRINT,
                    PermissionConstants.ZRA_VIEW,
                    PermissionConstants.ZRA_MANAGE,
                    PermissionConstants.REPORT_VIEW,
                    PermissionConstants.PRODUCT_IMPORT,
                    PermissionConstants.SYNC_DEVICE,
                    PermissionConstants.DEVICE_MANAGE,
                    PermissionConstants.RECONCILIATION_VIEW,
                    PermissionConstants.RECONCILIATION_MANAGE,
                    PermissionConstants.MERCHANT_MANAGE,
                    PermissionConstants.SUPPORT_MANAGE,
                    PermissionConstants.WHATSAPP_SEND
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

            // ADMIN gets all permissions
            assignPermissions(adminRole, permissionNames, permissionRepository, roleRepository);

            // MANAGER gets most permissions except delete for critical entities, and not payment method management
            assignPermissions(
                    managerRole,
                    List.of(
                            PermissionConstants.PRODUCT_VIEW,
                            PermissionConstants.PRODUCT_CREATE,
                            PermissionConstants.PRODUCT_UPDATE,
                            PermissionConstants.CATEGORY_VIEW,
                            PermissionConstants.CATEGORY_CREATE,
                            PermissionConstants.CATEGORY_UPDATE,
                            PermissionConstants.USER_VIEW,
                            PermissionConstants.BRANCH_VIEW,
                            PermissionConstants.BRANCH_CREATE,
                            PermissionConstants.BRANCH_UPDATE,
                            PermissionConstants.CUSTOMER_VIEW,
                            PermissionConstants.CUSTOMER_CREATE,
                            PermissionConstants.CUSTOMER_UPDATE,
                            PermissionConstants.INVENTORY_VIEW,
                            PermissionConstants.INVENTORY_ADJUST,
                            PermissionConstants.SALE_VIEW,
                            PermissionConstants.SALE_CREATE,
                            PermissionConstants.SALE_VOID,
                            PermissionConstants.SALE_REFUND,
                            PermissionConstants.RECEIPT_VIEW,
                            PermissionConstants.RECEIPT_REPRINT,
                            PermissionConstants.REPORT_VIEW,
                            PermissionConstants.PAYMENT_METHOD_VIEW,
                            PermissionConstants.ZRA_VIEW,
                            PermissionConstants.PRODUCT_IMPORT,
                            PermissionConstants.SYNC_DEVICE,
                            PermissionConstants.DEVICE_MANAGE,
                            PermissionConstants.RECONCILIATION_VIEW,
                            PermissionConstants.RECONCILIATION_MANAGE,
                            PermissionConstants.WHATSAPP_SEND
                    ),
                    permissionRepository,
                    roleRepository
            );

            // CASHIER gets view/create permissions only
            assignPermissions(
                    cashierRole,
                    List.of(
                            PermissionConstants.PRODUCT_VIEW,
                            PermissionConstants.CATEGORY_VIEW,
                            PermissionConstants.CUSTOMER_VIEW,
                            PermissionConstants.CUSTOMER_CREATE,
                            PermissionConstants.CUSTOMER_UPDATE,
                            PermissionConstants.SALE_VIEW,
                            PermissionConstants.SALE_CREATE,
                            PermissionConstants.RECEIPT_VIEW,
                            PermissionConstants.RECEIPT_REPRINT,
                            PermissionConstants.INVENTORY_VIEW,
                            PermissionConstants.PAYMENT_METHOD_VIEW,
                            PermissionConstants.SYNC_DEVICE,
                            PermissionConstants.WHATSAPP_SEND
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
