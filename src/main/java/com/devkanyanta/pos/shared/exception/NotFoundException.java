package com.devkanyanta.pos.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Requested resource does not exist (HTTP 404).
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(HttpStatus.NOT_FOUND, message, cause);
    }
}
