package com.mediconnect.exception;

/** 429: asked for a new one-time code too soon. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
