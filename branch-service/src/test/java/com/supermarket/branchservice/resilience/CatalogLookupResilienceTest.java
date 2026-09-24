package com.supermarket.branchservice.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.supermarket.branchservice.client.CatalogLookupService;
import com.supermarket.branchservice.client.ProductSummary;
import com.supermarket.commons.exception.RemoteServiceException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CatalogLookupResilienceTest {

    static final WireMockServer catalog = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        catalog.start();
    }

    @DynamicPropertySource
    static void catalogLocation(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.enabled", () -> "true");
        registry.add("spring.cloud.discovery.client.simple.instances.catalog-service[0].uri", catalog::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.default.read-timeout", () -> "500");
        registry.add("spring.cloud.openfeign.client.config.default.connect-timeout", () -> "500");
    }

    @AfterAll
    static void stop() {
        catalog.stop();
    }

    @Autowired
    private CatalogLookupService catalogLookupService;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        catalog.resetAll();
        circuitBreakerRegistry.circuitBreaker("catalog-service").reset();
    }

    @Test
    @DisplayName("a healthy catalog answers normally")
    void healthyCatalog_ShouldReturnProduct() {
        catalog.stubFor(get(urlEqualTo("/internal/products/1")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":1,\"name\":\"Leche Entera 1L\",\"category\":\"Lacteos\",\"price\":1.15}")));

        ProductSummary product = catalogLookupService.requireProduct(1L);

        assertThat(product.name()).isEqualTo("Leche Entera 1L");
    }

    @Test
    @DisplayName("a 404 from catalog becomes a controlled not-found error and is not retried")
    void unknownProduct_ShouldBeNotFoundWithoutRetry() {
        catalog.stubFor(get(urlEqualTo("/internal/products/99")).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":404,\"error\":\"Not Found\",\"message\":\"Product not found with ID: 99\"}")));

        assertThatThrownBy(() -> catalogLookupService.requireProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found with ID: 99");
        catalog.verify(1, getRequestedFor(urlEqualTo("/internal/products/99")));
    }

    @Test
    @DisplayName("a slow catalog times out quickly, is retried and ends in a controlled 503-style error")
    void slowCatalog_ShouldFailFastWithRemoteServiceException() {
        catalog.stubFor(get(urlEqualTo("/internal/products/1")).willReturn(aResponse()
                .withFixedDelay(2_000)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":1}")));

        long start = System.nanoTime();
        assertThatThrownBy(() -> catalogLookupService.requireProduct(1L))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("catalog-service is temporarily unavailable");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
        catalog.verify(3, getRequestedFor(urlEqualTo("/internal/products/1")));
    }

    @Test
    @DisplayName("a failing catalog on reads degrades to an empty lookup instead of breaking the inventory view")
    void failingCatalog_ShouldDegradeBatchLookup() {
        catalog.stubFor(get(urlPathEqualTo("/internal/products")).willReturn(aResponse().withStatus(500)));

        assertThat(catalogLookupService.productsById(List.of(1L, 2L))).isEmpty();
    }
}
