package com.devkanyanta.pos.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the authenticated username for audit fields. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
    }
}
