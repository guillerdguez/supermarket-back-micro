package com.supermarket.reportservice.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportCompositionIntegrationTest {

    static final WireMockServer remote = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        remote.start();
    }

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.enabled", () -> "true");
        for (String service : new String[]{"sales-service", "branch-service", "catalog-service", "auth-service"}) {
            registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri", remote::baseUrl);
        }
        registry.add("spring.cloud.openfeign.client.config.default.read-timeout", () -> "500");
    }

    @AfterAll
    static void stop() {
        remote.stop();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        remote.resetAll();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    @DisplayName("sales by branch is composed from sales-service and enriched with branch-service names")
    void salesByBranch_ShouldComposeServices() throws Exception {
        remote.stubFor(get(urlPathEqualTo("/internal/reports/sales/by-branch")).willReturn(json(
                "[{\"branchId\":1,\"branchName\":\"Old name\",\"totalRevenue\":1350.59,\"transactionCount\":74}]")));
        remote.stubFor(get(urlPathEqualTo("/internal/branches")).willReturn(json(
                "[{\"id\":1,\"name\":\"Sucursal Centro\",\"address\":\"Calle de la Paz\",\"isWarehouse\":false,\"active\":true}]")));

        mockMvc.perform(as("ADMIN", "/reports/sales/by-branch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchName").value("Sucursal Centro"))
                .andExpect(jsonPath("$[0].transactionCount").value(74));
    }

    @Test
    @DisplayName("when branch-service is down the report still answers using the name frozen in sales")
    void salesByBranch_WhenBranchDown_ShouldDegrade() throws Exception {
        remote.stubFor(get(urlPathEqualTo("/internal/reports/sales/by-branch")).willReturn(json(
                "[{\"branchId\":1,\"branchName\":\"Sucursal Centro\",\"totalRevenue\":1350.59,\"transactionCount\":74}]")));
        remote.stubFor(get(urlPathEqualTo("/internal/branches")).willReturn(aResponse().withStatus(503)));

        mockMvc.perform(as("MANAGER", "/reports/sales/by-branch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchName").value("Sucursal Centro"));
    }

    @Test
    @DisplayName("when sales-service is down the report fails with a controlled 503")
    void salesSummary_WhenSalesDown_ShouldReturn503() throws Exception {
        remote.stubFor(get(urlPathEqualTo("/internal/reports/sales/summary")).willReturn(aResponse().withStatus(500)));

        mockMvc.perform(as("ADMIN", "/reports/sales/summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("sales-service is temporarily unavailable. Please try again later."));
    }

    @Test
    @DisplayName("reports stay restricted to ADMIN and MANAGER as in the monolith")
    void cashier_ShouldBeForbidden() throws Exception {
        mockMvc.perform(as("CASHIER", "/reports/sales/summary"))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder as(String role, String path) {
        return request(HttpMethod.GET, path)
                .header("X-User-Id", "1")
                .header("X-User-Email", "user@supermarket.com")
                .header("X-User-Name", "user")
                .header("X-User-Role", role);
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
