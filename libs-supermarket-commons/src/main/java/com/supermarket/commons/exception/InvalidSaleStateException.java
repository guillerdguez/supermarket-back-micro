package com.supermarket.commons.exception;

public class InvalidSaleStateException extends RuntimeException {
    public InvalidSaleStateException(String message) {
        super(message);
    }
}
