package com.supermarket.salesservice.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.supermarket.salesservice.event.SalesEventPublisher;
import com.supermarket.salesservice.model.cashregister.CashRegister;
import com.supermarket.salesservice.model.cashregister.CashRegisterStatus;
import com.supermarket.salesservice.repository.CashRegisterRepository;
import com.supermarket.salesservice.repository.PaymentRepository;
import com.supermarket.salesservice.repository.SaleRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SaleResilienceIntegrationTest {

    static final WireMockServer remote = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        remote.start();
    }

    private static final String SALE = """
            {
              "branchId": 1,
              "details": [{"productId": 10, "quantity": 2}],
              "amount": 2.40,
              "paymentType": "CASH"
            }
            """;

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.enabled", () -> "true");
        registry.add("spring.cloud.discovery.client.simple.instances.catalog-service[0].uri", remote::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.branch-service[0].uri", remote::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.default.read-timeout", () -> "500");
        registry.add("spring.cloud.openfeign.client.config.default.connect-timeout", () -> "500");
    }

    @AfterAll
    static void stop() {
        remote.stop();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SaleRepository saleRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private CashRegisterRepository cashRegisterRepository;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private SalesEventPublisher salesEventPublisher;

    @BeforeEach
    void setUp() {
        remote.resetAll();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        paymentRepository.deleteAll();
        saleRepository.deleteAll();
        cashRegisterRepository.deleteAll();
        cashRegisterRepository.save(CashRegister.builder()
                .branchId(1L).branchName("Sucursal Centro")
                .openingBalance(new BigDecimal("100.00")).openingTime(LocalDateTime.now())
                .status(CashRegisterStatus.OPEN).openedById(3L).openedByUsername("cashier1")
                .build());
        remote.stubFor(get(urlEqualTo("/internal/branches/1")).willReturn(json(
                "{\"id\":1,\"name\":\"Sucursal Centro\",\"address\":\"Calle de la Paz 12\",\"isWarehouse\":false,\"active\":true}")));
    }

    @Test
    @DisplayName("with every dependency healthy the sale is created and stock is reduced with an idempotency key")
    void healthyDependencies_ShouldCreateSale() throws Exception {
        givenCatalogPrice();
        remote.stubFor(post(urlEqualTo("/internal/inventory/branches/1/decrease")).willReturn(json(
                "{\"branchId\":1,\"idempotencyKey\":\"k\",\"operation\":\"DECREASE\",\"applied\":true,\"items\":[]}")));

        createSale().andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(2.40))
                .andExpect(jsonPath("$.branchName").value("Sucursal Centro"))
                .andExpect(jsonPath("$.details[0].productName").value("Pan de Barra Unidad"));

        remote.verify(postRequestedFor(urlEqualTo("/internal/inventory/branches/1/decrease"))
                .withHeader("Idempotency-Key", matching("sale-.*-decrease")));
        assertThat(saleRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("catalog-service down: the sale fails fast with 503 and nothing is persisted or discounted")
    void catalogDown_ShouldFailFastWithoutSale() throws Exception {
        remote.stubFor(get(urlPathEqualTo("/internal/products")).willReturn(aResponse().withStatus(500)));

        createSale().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("catalog-service is temporarily unavailable. Please try again later."));

        remote.verify(0, postRequestedFor(urlPathEqualTo("/internal/inventory/branches/1/decrease")));
        assertThat(saleRepository.count()).isZero();
    }

    @Test
    @DisplayName("branch-service hanging: the stock call times out, retries with the same key and no sale is left half done")
    void branchTimeout_ShouldRetryWithSameKeyAndNotPersist() throws Exception {
        givenCatalogPrice();
        remote.stubFor(post(urlEqualTo("/internal/inventory/branches/1/decrease"))
                .willReturn(aResponse().withFixedDelay(2_000).withStatus(200)));

        long start = System.nanoTime();
        createSale().andExpect(status().isServiceUnavailable());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
        var requests = remote.findAll(postRequestedFor(urlEqualTo("/internal/inventory/branches/1/decrease")));
        assertThat(requests).hasSize(3);
        assertThat(requests).extracting(request -> request.getHeader("Idempotency-Key")).containsOnly(
                requests.get(0).getHeader("Idempotency-Key"));
        assertThat(saleRepository.count()).isZero();
    }

    @Test
    @DisplayName("insufficient stock reported by branch-service becomes a 400 without retries or sale")
    void insufficientStock_ShouldReturn400WithoutRetry() throws Exception {
        givenCatalogPrice();
        remote.stubFor(post(urlEqualTo("/internal/inventory/branches/1/decrease")).willReturn(aResponse()
                .withStatus(400).withHeader("Content-Type", "application/json")
                .withBody("{\"status\":400,\"error\":\"Inventory Conflict\",\"message\":\"Insufficient stock for product 'Pan de Barra Unidad' (ID: 10) in branch 1. Available: 1, required: 2\"}")));

        createSale().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Inventory Conflict"));

        remote.verify(1, postRequestedFor(urlEqualTo("/internal/inventory/branches/1/decrease")));
        assertThat(saleRepository.count()).isZero();
    }

    @Test
    @DisplayName("after repeated catalog failures the circuit opens and later sales are rejected once, without calling it or retrying")
    void repeatedFailures_ShouldOpenCircuit() throws Exception {
        remote.stubFor(get(urlPathEqualTo("/internal/products")).willReturn(aResponse().withStatus(503)));

        for (int i = 0; i < 4; i++) {
            createSale().andExpect(status().isServiceUnavailable());
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("catalog-service").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        CircuitBreaker.Metrics metrics = circuitBreakerRegistry.circuitBreaker("catalog-service").getMetrics();
        long rejectedBefore = metrics.getNumberOfNotPermittedCalls();
        int callsBefore = remote.findAll(getRequestedFor(urlPathEqualTo("/internal/products"))).size();
        createSale().andExpect(status().isServiceUnavailable());
        int callsAfter = remote.findAll(getRequestedFor(urlPathEqualTo("/internal/products"))).size();

        assertThat(callsAfter).isEqualTo(callsBefore);
        assertThat(metrics.getNumberOfNotPermittedCalls()).isEqualTo(rejectedBefore + 1);
    }

    private void givenCatalogPrice() {
        remote.stubFor(get(urlPathEqualTo("/internal/products")).withQueryParam("ids", equalTo("10")).willReturn(json(
                "[{\"id\":10,\"name\":\"Pan de Barra Unidad\",\"category\":\"Panaderia\",\"price\":1.20}]")));
    }

    private ResultActions createSale() throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/sales")
                .header("X-User-Id", "3")
                .header("X-User-Email", "cashier@supermarket.com")
                .header("X-User-Name", "cashier1")
                .header("X-User-Role", "CASHIER")
                .header("X-User-Branch-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SALE));
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
