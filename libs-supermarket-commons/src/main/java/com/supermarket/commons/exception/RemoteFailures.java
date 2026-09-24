package com.supermarket.commons.exception;

import java.util.Set;

public final class RemoteFailures {

    private static final Set<Class<? extends RuntimeException>> BUSINESS_EXCEPTIONS = Set.of(
            ResourceNotFoundException.class,
            DuplicateResourceException.class,
            InvalidOperationException.class,
            InsufficientStockException.class,
            InsufficientPermissionsException.class,
            InvalidSaleStateException.class
    );

    private static final String CALL_NOT_PERMITTED = "io.github.resilience4j.circuitbreaker.CallNotPermittedException";

    private RemoteFailures() {
    }

    public static boolean isBusinessError(Throwable throwable) {
        return throwable != null && BUSINESS_EXCEPTIONS.stream().anyMatch(type -> type.isInstance(throwable));
    }

    public static RuntimeException propagate(String serviceName, Throwable throwable) {
        if (isBusinessError(throwable)) {
            return (RuntimeException) throwable;
        }
        String message = serviceName + " is temporarily unavailable. Please try again later.";
        if (throwable != null && CALL_NOT_PERMITTED.equals(throwable.getClass().getName())) {
            return new CircuitOpenException(serviceName, message, throwable);
        }
        return new RemoteServiceException(serviceName, message, throwable);
    }
}
