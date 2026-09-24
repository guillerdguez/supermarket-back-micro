package com.supermarket.auditservice.integration;

import com.supermarket.auditservice.event.AuthActivityEvent;
import com.supermarket.auditservice.model.audit.AuditLog;
import com.supermarket.auditservice.model.audit.AuditStatus;
import com.supermarket.auditservice.repository.AuditLogRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=true")
@ActiveProfiles("test")
@Testcontainers
class AuditKafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("login success, login failure and logout events published by auth-service become audit logs")
    void authEvents_ShouldBeAudited() {
        KafkaTemplate<String, Object> authProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                JsonSerializer.TYPE_MAPPINGS, "authActivity:" + AuthActivityEvent.class.getName())));

        authProducer.send("auth.login.success", "admin@supermarket.com", event("auth.login.success",
                "LOGIN_SUCCESS", "User logged in successfully", "SUCCESS"));
        authProducer.send("auth.login.failed", "admin@supermarket.com", event("auth.login.failed",
                "LOGIN_FAILED", "Invalid credentials", "FAILED"));
        authProducer.send("auth.logout", "admin@supermarket.com", event("auth.logout",
                "LOGOUT", "User logged out from IP: 10.0.0.7", "SUCCESS"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(auditLogRepository.findAll())
                        .extracting(AuditLog::getAction)
                        .containsExactlyInAnyOrder("LOGIN_SUCCESS", "LOGIN_FAILED", "LOGOUT"));
        assertThat(auditLogRepository.findAll())
                .filteredOn(log -> log.getAction().equals("LOGIN_FAILED"))
                .singleElement()
                .satisfies(log -> assertThat(log.getStatus()).isEqualTo(AuditStatus.FAILED));
    }

    private AuthActivityEvent event(String type, String action, String details, String status) {
        return new AuthActivityEvent(UUID.randomUUID().toString(), type, LocalDateTime.now(),
                "admin@supermarket.com", action, details, "10.0.0.7", status);
    }
}
