package com.devkanyanta.pos.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Feature is not available on the merchant's current plan (HTTP 403).
 */
public class PlanRestrictionException extends ApiException {

    public PlanRestrictionException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }

    public PlanRestrictionException(String message, Throwable cause) {
        super(HttpStatus.FORBIDDEN, message, cause);
    }
}
