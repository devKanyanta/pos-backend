package com.devkanyanta.pos.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Request conflicts with the current state of a resource (HTTP 409).
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }

    public ConflictException(String message, Throwable cause) {
        super(HttpStatus.CONFLICT, message, cause);
    }
}
