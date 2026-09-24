package com.supermarket.notificationservice.client;

import com.supermarket.commons.exception.RemoteFailures;
import com.supermarket.notificationservice.model.notification.Recipient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipientDirectory {

    private static final String AUTH_SERVICE = "auth-service";
    private static final List<String> MANAGEMENT_ROLES = List.of("ADMIN", "MANAGER");

    private final UserClient userClient;

    @CircuitBreaker(name = AUTH_SERVICE, fallbackMethod = "managersFallback")
    @Retry(name = AUTH_SERVICE)
    public List<Recipient> managers() {
        return userClient.getByRoles(MANAGEMENT_ROLES).stream()
                .map(user -> new Recipient(user.id(), user.username()))
                .toList();
    }

    private List<Recipient> managersFallback(Throwable throwable) {
        throw RemoteFailures.propagate(AUTH_SERVICE, throwable);
    }
}
