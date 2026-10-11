package com.mediconnect.exception;

/** 409 - a unique value or an overlapping record already exists. */
public class AlreadyExistException extends RuntimeException {

    public AlreadyExistException(String message) {
        super(message);
    }
}
