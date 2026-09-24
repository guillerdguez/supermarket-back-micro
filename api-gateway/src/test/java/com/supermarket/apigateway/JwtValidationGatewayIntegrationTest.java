package com.supermarket.apigateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class JwtValidationGatewayIntegrationTest {

    static final WireMockServer backend = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        backend.start();
    }

    @DynamicPropertySource
    static void routeToWireMock(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri", backend::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.catalog-service[0].uri", backend::baseUrl);
    }

    @AfterAll
    static void stopBackend() {
        backend.stop();
    }

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    void resetStubs() {
        backend.resetAll();
        backend.stubFor(get(urlEqualTo("/products")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));
    }

    @Test
    @DisplayName("should reject a protected route without bearer token and never call the backend")
    void missingToken_ShouldReturn401() {
        webTestClient.get().uri("/api/products")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Invalid or expired token");

        backend.verify(0, getRequestedFor(urlEqualTo("/products")));
    }

    @Test
    @DisplayName("should forward the identity returned by auth-service and drop spoofed identity headers")
    void validToken_ShouldForwardIdentityHeaders() {
        backend.stubFor(get(urlEqualTo("/auth/validate"))
                .withHeader("Authorization", equalTo("Bearer good-token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("X-User-Id", "3")
                        .withHeader("X-User-Email", "cashier@supermarket.com")
                        .withHeader("X-User-Name", "cashier1")
                        .withHeader("X-User-Role", "CASHIER")
                        .withHeader("X-User-Branch-Id", "1")));

        webTestClient.get().uri("/api/products")
                .header("Authorization", "Bearer good-token")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        backend.verify(getRequestedFor(urlEqualTo("/products"))
                .withHeader("X-User-Id", equalTo("3"))
                .withHeader("X-User-Role", equalTo("CASHIER"))
                .withHeader("X-User-Branch-Id", equalTo("1")));
    }

    @Test
    @DisplayName("should translate the auth-service rejection reason into a JSON 401")
    void rejectedToken_ShouldReturnReason() {
        backend.stubFor(get(urlEqualTo("/auth/validate"))
                .willReturn(aResponse().withStatus(401).withHeader("X-Auth-Error", "Token expired")));

        webTestClient.get().uri("/api/products")
                .header("Authorization", "Bearer old-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Token expired");

        backend.verify(0, getRequestedFor(urlEqualTo("/products")));
    }

    @Test
    @DisplayName("should strip spoofed identity headers on public routes too")
    void publicRoute_ShouldStripSpoofedHeaders() {
        backend.stubFor(get(urlEqualTo("/auth/ping")).willReturn(aResponse().withStatus(200)));

        webTestClient.get().uri("/api/auth/ping")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        backend.verify(getRequestedFor(urlEqualTo("/auth/ping")).withoutHeader("X-User-Role"));
    }

    @Test
    @DisplayName("should answer with the circuit breaker fallback when the target service has no instances")
    void unavailableService_ShouldReturnFallback() {
        backend.stubFor(get(urlEqualTo("/auth/validate"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("X-User-Id", "1")
                        .withHeader("X-User-Role", "ADMIN")));

        webTestClient.get().uri("/api/transfers")
                .header("Authorization", "Bearer good-token")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.message")
                .isEqualTo("transfer-service is temporarily unavailable. Please try again later.");
    }
}
