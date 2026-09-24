package com.supermarket.commons.exception;

public class CircuitOpenException extends RemoteServiceException {

    public CircuitOpenException(String serviceName, String message, Throwable cause) {
        super(serviceName, message, cause);
    }
}
