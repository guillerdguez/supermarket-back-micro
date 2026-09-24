package com.supermarket.authservice.exception;

public class RateLimitServiceException extends RuntimeException {
    public RateLimitServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}