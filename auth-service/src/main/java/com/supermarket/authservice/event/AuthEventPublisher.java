package com.supermarket.authservice.event;

import com.supermarket.authservice.web.ClientIpResolver;
import com.supermarket.commons.kafka.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuthEventPublisher {

    private final DomainEventPublisher domainEventPublisher;

    public void loginSucceeded(String username) {
        publish(AuthTopics.LOGIN_SUCCESS, "LOGIN_SUCCESS", username, "User logged in successfully", "SUCCESS");
    }

    public void loginFailed(String username, String reason) {
        publish(AuthTopics.LOGIN_FAILED, "LOGIN_FAILED", username, reason, "FAILED");
    }

    public void loggedOut(String username) {
        publish(AuthTopics.LOGOUT, "LOGOUT", username,
                "User logged out from IP: " + ClientIpResolver.currentClientIp(), "SUCCESS");
    }

    private void publish(String topic, String action, String username, String details, String status) {
        AuthActivityEvent event = new AuthActivityEvent(
                UUID.randomUUID().toString(),
                topic,
                LocalDateTime.now(),
                username,
                action,
                details,
                ClientIpResolver.currentClientIp(),
                status);
        domainEventPublisher.publish(topic, event);
    }
}
