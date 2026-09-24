package com.supermarket.auditservice.event;

import com.supermarket.auditservice.service.business.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthActivityListener {

    public static final String LOGIN_SUCCESS = "auth.login.success";
    public static final String LOGIN_FAILED = "auth.login.failed";
    public static final String LOGOUT = "auth.logout";

    private final AuditService auditService;

    @KafkaListener(topics = {LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT})
    public void onAuthActivity(AuthActivityEvent event) {
        auditService.record(event);
    }
}
