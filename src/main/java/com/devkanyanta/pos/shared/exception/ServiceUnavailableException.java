package com.devkanyanta.pos.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * A required external integration is not configured or not reachable (HTTP 503).
 */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
