package com.mediconnect.exception;

/** 400 - the request breaks a business rule. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
