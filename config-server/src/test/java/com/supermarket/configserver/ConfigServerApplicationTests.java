package com.supermarket.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.config.server.native.search-locations=file:../config-repo/")
class ConfigServerApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void servesSharedConfigurationForAnyService() {
        Map<?, ?> response = restTemplate.getForObject("/sales-service/default", Map.class);

        assertThat(response.get("name")).isEqualTo("sales-service");
        assertThat((List<?>) response.get("propertySources")).isNotEmpty();
    }
}
