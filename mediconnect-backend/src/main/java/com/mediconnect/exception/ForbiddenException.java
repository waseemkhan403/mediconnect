package com.mediconnect.exception;

/**
 * 403 decided by a service rule, e.g. a patient trying to mark their own appointment COMPLETED.
 * (Role checks on endpoints are @PreAuthorize and answered by SecurityConfig's AccessDeniedHandler.)
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
