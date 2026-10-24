package com.devkanyanta.pos.shared.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request
    ) {
        log.error("Access denied: {}", ex.getMessage());

        Map<String, Object> response = buildErrorResponse(
            HttpStatus.FORBIDDEN,
            "Access Denied",
            "You do not have permission to access this resource. Ensure you are logged in with a valid token.",
            request
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentialsException(
            BadCredentialsException ex,
            WebRequest request
    ) {
        log.error("Bad credentials: {}", ex.getMessage());

        Map<String, Object> response = buildErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "Invalid Credentials",
            "The provided username or password is incorrect.",
            request
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException ex,
            WebRequest request
    ) {
        log.error("Authentication exception: {}", ex.getMessage());

        Map<String, Object> response = buildErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "Authentication Failed",
            "Authentication failed. Please check your credentials and ensure your token is valid. Error: " + ex.getMessage(),
            request
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Business-rule failures thrown by services (validation, not found, conflicts, plan limits).
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(
            ApiException ex,
            WebRequest request
    ) {
        HttpStatus status = ex.getStatus();

        if (status.is5xxServerError()) {
            log.error("API error ({}): {}", status.value(), ex.getMessage(), ex);
        } else {
            log.warn("API error ({}): {}", status.value(), ex.getMessage());
        }

        return ResponseEntity.status(status)
                .body(buildErrorResponse(status, status.getReasonPhrase(), ex.getMessage(), request));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class,
            MultipartException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(
            Exception ex,
            WebRequest request
    ) {
        log.warn("Malformed request: {}", ex.getMessage());

        String message = switch (ex) {
            case MethodArgumentTypeMismatchException mismatch ->
                    "Invalid value for parameter '" + mismatch.getName() + "'";
            case MissingServletRequestParameterException missing ->
                    "Missing required parameter '" + missing.getParameterName() + "'";
            case MissingServletRequestPartException missingPart ->
                    "Missing required file part '" + missingPart.getRequestPartName() + "'";
            case HttpMessageNotReadableException ignored ->
                    "Request body is missing or is not valid JSON";
            default -> ex.getMessage();
        };

        return ResponseEntity.badRequest()
                .body(buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", message, request));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            WebRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(buildErrorResponse(HttpStatus.CONTENT_TOO_LARGE, "Payload Too Large",
                        "Uploaded file is too large", request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            WebRequest request
    ) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(HttpStatus.CONFLICT, "Conflict",
                        "The request conflicts with existing data (duplicate value or record still in use).",
                        request));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> handleLockingFailure(
            RuntimeException ex,
            WebRequest request
    ) {
        log.warn("Concurrent modification: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(HttpStatus.CONFLICT, "Conflict",
                        "The record was modified concurrently. Please retry.", request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(
            NoResourceFoundException ex,
            WebRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found",
                        "No endpoint matches this path", request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            WebRequest request
    ) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed",
                        ex.getMessage(), request));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            WebRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(buildErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                        ex.getMessage(), request));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex,
            WebRequest request
    ) {
        log.error("Runtime exception: {}", ex.getMessage(), ex);

        Map<String, Object> response = buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            ex.getMessage(),
            request
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(
            Exception ex,
            WebRequest request
    ) {
        log.error("Unexpected exception: {}", ex.getMessage(), ex);

        Map<String, Object> response = buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Unexpected Error",
            "An unexpected error occurred. Please contact support if the problem persists.",
            request
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private Map<String, Object> buildErrorResponse(
            HttpStatus status,
            String error,
            String message,
            WebRequest request
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", status.value());
        response.put("error", error);
        response.put("message", message);
        response.put("path", request.getDescription(false).replace("uri=", ""));
        return response;
    }
}
