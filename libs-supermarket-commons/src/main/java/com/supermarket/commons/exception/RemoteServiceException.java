package com.supermarket.commons.exception;

import lombok.Getter;

@Getter
public class RemoteServiceException extends RuntimeException {
    private final String serviceName;

    public RemoteServiceException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }

    public RemoteServiceException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }
}
