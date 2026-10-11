package com.mediconnect.exception;

/** 401: sign-in failed, or a refresh token is missing, expired or revoked. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
