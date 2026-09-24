package com.supermarket.branchservice.integration;

import com.supermarket.branchservice.client.CatalogLookupService;
import com.supermarket.branchservice.event.StockEventPublisher;
import com.supermarket.branchservice.model.branch.Branch;
import com.supermarket.branchservice.model.branch.BranchInventory;
import com.supermarket.branchservice.repository.BranchInventoryRepository;
import com.supermarket.branchservice.repository.BranchRepository;
import com.supermarket.branchservice.repository.ProcessedStockOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockIdempotencyIntegrationTest {

    private static final String BODY = """
            {"items": [{"productId": 1, "quantity": 4}]}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private BranchInventoryRepository branchInventoryRepository;
    @Autowired
    private ProcessedStockOperationRepository processedOperationRepository;

    @MockitoBean
    private CatalogLookupService catalogLookupService;
    @MockitoBean
    private StockEventPublisher stockEventPublisher;

    private Long branchId;

    @BeforeEach
    void setUp() {
        processedOperationRepository.deleteAll();
        branchInventoryRepository.deleteAll();
        branchRepository.deleteAll();
        Branch branch = branchRepository.save(Branch.builder().name("Sucursal Centro").address("Calle de la Paz 12").build());
        branchId = branch.getId();
        branchInventoryRepository.save(BranchInventory.builder()
                .branch(branch).productId(1L).stock(20).minStock(2).build());
        given(catalogLookupService.productsById(any())).willReturn(Map.of());
    }

    @Test
    @DisplayName("a retried decrease with the same Idempotency-Key discounts the stock only once")
    void retriedDecrease_ShouldApplyOnce() throws Exception {
        mockMvc.perform(post("/internal/inventory/branches/{id}/decrease", branchId)
                        .header("Idempotency-Key", "sale-abc-decrease")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true));

        mockMvc.perform(post("/internal/inventory/branches/{id}/decrease", branchId)
                        .header("Idempotency-Key", "sale-abc-decrease")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false));

        assertThat(currentStock()).isEqualTo(16);
    }

    @Test
    @DisplayName("concurrent retries with the same key still discount the stock only once")
    void concurrentRetries_ShouldApplyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            calls.add(() -> mockMvc.perform(post("/internal/inventory/branches/{id}/decrease", branchId)
                            .header("Idempotency-Key", "sale-concurrent")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : pool.invokeAll(calls)) {
            statuses.add(future.get());
        }
        pool.shutdown();

        assertThat(currentStock()).isEqualTo(16);
        assertThat(statuses).contains(200);
    }

    @Test
    @DisplayName("a decrease without enough stock fails and does not consume the key, so a later retry can succeed")
    void failedDecrease_ShouldNotStoreKey() throws Exception {
        mockMvc.perform(post("/internal/inventory/branches/{id}/decrease", branchId)
                        .header("Idempotency-Key", "sale-too-big")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"productId\": 1, \"quantity\": 50}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Inventory Conflict"));

        assertThat(processedOperationRepository.existsById("sale-too-big")).isFalse();
        assertThat(currentStock()).isEqualTo(20);
    }

    private Integer currentStock() {
        return branchInventoryRepository.findByBranchIdAndProductId(branchId, 1L).orElseThrow().getStock();
    }
}
