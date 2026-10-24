package com.devkanyanta.pos.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Request is invalid or violates a business rule (HTTP 400).
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, message, cause);
    }
}
