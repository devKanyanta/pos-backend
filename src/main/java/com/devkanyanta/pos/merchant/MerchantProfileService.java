package com.devkanyanta.pos.merchant;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.merchant.dto.MerchantProfileResponse;
import com.devkanyanta.pos.merchant.dto.UpdateMerchantProfileRequest;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.PlanRestrictionException;
import com.devkanyanta.pos.shared.util.Texts;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MerchantProfileService {

    public static final String FEATURE_ZRA = "zra";
    public static final String FEATURE_MULTI_USER = "multiUser";
    public static final String FEATURE_REPORTS = "reports";
    public static final String FEATURE_RECONCILIATION = "reconciliation";

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MerchantProfileRepository repository;

    /** Returns the merchant profile, creating it (with a new account id) on first use. */
    @Transactional
    public MerchantProfileEntity getOrCreate() {

        return repository.findFirstByOrderByIdAsc()
                .orElseGet(() -> repository.save(MerchantProfileEntity.builder()
                        .accountId(generateAccountId())
                        .plan(MerchantPlan.SME)
                        .build()));
    }

    @Transactional
    public MerchantProfileResponse getProfile() {
        return toResponse(getOrCreate());
    }

    @Transactional
    public MerchantProfileResponse updateProfile(UpdateMerchantProfileRequest request) {

        if (request == null) {
            throw new BadRequestException("Merchant profile request is required");
        }

        MerchantProfileEntity profile = getOrCreate();

        if (request.getBusinessName() != null) {
            profile.setBusinessName(Texts.clean(request.getBusinessName()));
        }

        if (request.getTpin() != null) {
            String tpin = Texts.clean(request.getTpin());
            if (tpin != null && !tpin.matches("\\d{10}")) {
                throw new BadRequestException("TPIN must be a 10-digit numeric value");
            }
            profile.setTpin(tpin);
        }

        if (request.getPhone() != null) {
            profile.setPhone(Texts.clean(request.getPhone()));
        }

        if (request.getEmail() != null) {
            profile.setEmail(Texts.clean(request.getEmail()));
        }

        if (request.getAddress() != null) {
            profile.setAddress(Texts.clean(request.getAddress()));
        }

        if (request.getReceiptFooter() != null) {
            profile.setReceiptFooter(Texts.truncate(Texts.clean(request.getReceiptFooter()), 500));
        }

        if (StringUtils.hasText(request.getPlan())) {
            try {
                profile.setPlan(MerchantPlan.valueOf(request.getPlan().trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Plan must be one of MICRO, SME");
            }
        }

        return toResponse(repository.save(profile));
    }

    @Transactional
    public MerchantPlan currentPlan() {
        return getOrCreate().getPlan();
    }

    @Transactional
    public boolean isFeatureEnabled(String feature) {
        return currentPlan() == MerchantPlan.SME || !Map.of(
                FEATURE_ZRA, true,
                FEATURE_MULTI_USER, true,
                FEATURE_REPORTS, true,
                FEATURE_RECONCILIATION, true
        ).containsKey(feature);
    }

    /** Throws 403 when the feature is not part of the current plan. */
    @Transactional
    public void requireFeature(String feature, String description) {
        if (!isFeatureEnabled(feature)) {
            throw new PlanRestrictionException(description + " is not available on the MICRO plan. Upgrade to SME.");
        }
    }

    private MerchantProfileResponse toResponse(MerchantProfileEntity profile) {

        Map<String, Boolean> features = new LinkedHashMap<>();
        boolean sme = profile.getPlan() == MerchantPlan.SME;
        features.put(FEATURE_ZRA, sme);
        features.put(FEATURE_MULTI_USER, sme);
        features.put(FEATURE_REPORTS, sme);
        features.put(FEATURE_RECONCILIATION, sme);

        return MerchantProfileResponse.builder()
                .accountId(profile.getAccountId())
                .businessName(profile.getBusinessName())
                .tpin(profile.getTpin())
                .phone(profile.getPhone())
                .email(profile.getEmail())
                .address(profile.getAddress())
                .receiptFooter(profile.getReceiptFooter())
                .plan(profile.getPlan().name())
                .features(features)
                .build();
    }

    private static String generateAccountId() {

        StringBuilder id = new StringBuilder("POS-");
        for (int i = 0; i < 8; i++) {
            id.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }
}
