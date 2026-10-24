package com.devkanyanta.pos.merchant;

/**
 * Subscription tiers.
 * <ul>
 *   <li>{@code MICRO} – free tier: offline sales (cash + mobile money), basic stock deduction,
 *       WhatsApp receipt sharing and today's total only. No ZRA, single user, no reports.</li>
 *   <li>{@code SME} – paid tier: everything, including ZRA VSDC, multi-user and reconciliation.</li>
 * </ul>
 */
public enum MerchantPlan {
    MICRO, SME
}
