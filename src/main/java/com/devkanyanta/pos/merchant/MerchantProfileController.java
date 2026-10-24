package com.devkanyanta.pos.merchant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.merchant.dto.MerchantProfileResponse;
import com.devkanyanta.pos.merchant.dto.UpdateMerchantProfileRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class MerchantProfileController {

    private final MerchantProfileService merchantProfileService;

    /** Any signed-in user can read the profile (receipt header, account id, plan features). */
    @GetMapping
    public MerchantProfileResponse getProfile() {
        return merchantProfileService.getProfile();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.MERCHANT_MANAGE + "')")
    public MerchantProfileResponse updateProfile(
            @RequestBody UpdateMerchantProfileRequest request
    ) {
        return merchantProfileService.updateProfile(request);
    }
}
