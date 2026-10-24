package com.devkanyanta.pos.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for business errors that map to a specific HTTP status.
 * Handled by {@link GlobalExceptionHandler}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    protected ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
