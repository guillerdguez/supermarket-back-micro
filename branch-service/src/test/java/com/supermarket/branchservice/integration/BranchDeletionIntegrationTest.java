package com.supermarket.branchservice.integration;

import com.supermarket.branchservice.client.BranchUsageService;
import com.supermarket.branchservice.client.CatalogLookupService;
import com.supermarket.branchservice.client.SalesBranchUsage;
import com.supermarket.branchservice.dto.branch.BranchResponse;
import com.supermarket.branchservice.event.StockEventPublisher;
import com.supermarket.branchservice.model.branch.Branch;
import com.supermarket.branchservice.model.branch.BranchInventory;
import com.supermarket.branchservice.repository.BranchInventoryRepository;
import com.supermarket.branchservice.repository.BranchRepository;
import com.supermarket.branchservice.service.business.BranchService;
import com.supermarket.commons.exception.InvalidOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static com.supermarket.branchservice.fixtures.branch.BranchFixtures.validBranchRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class BranchDeletionIntegrationTest {

    @Autowired
    private BranchService branchService;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @MockitoBean
    private CatalogLookupService catalogLookupService;
    @MockitoBean
    private BranchUsageService branchUsageService;
    @MockitoBean
    private StockEventPublisher stockEventPublisher;

    @BeforeEach
    void setUp() {
        branchInventoryRepository.deleteAll();
        branchRepository.deleteAll();
        given(catalogLookupService.allProductIds()).willReturn(List.of(1L));
        given(catalogLookupService.productsById(any())).willReturn(java.util.Map.of());
        given(branchUsageService.salesUsage(anyLong())).willReturn(new SalesBranchUsage(false, false));
        given(branchUsageService.hasTransfers(anyLong())).willReturn(false);
        given(branchUsageService.hasUsers(anyLong())).willReturn(false);
    }

    @Test
    @DisplayName("DELETE - should delete a branch whose only inventory rows are auto-created placeholders (stock = 0)")
    void delete_branchWithPlaceholderInventoryOnly_shouldSucceed() {
        BranchResponse created = branchService.create(validBranchRequest());

        List<BranchInventory> placeholders = branchInventoryRepository.findByBranchId(created.getId());
        assertThat(placeholders).hasSize(1);
        assertThat(placeholders.get(0).getStock()).isZero();

        branchService.delete(created.getId());

        assertThat(branchRepository.existsById(created.getId())).isFalse();
        assertThat(branchInventoryRepository.findByBranchId(created.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE - a branch with stock cannot be deleted, but can be deactivated and keeps its row")
    void delete_branchWithActivity_shouldFailButAllowDeactivation() {
        BranchResponse created = branchService.create(validBranchRequest());

        BranchInventory inventory = branchInventoryRepository.findByBranchId(created.getId()).get(0);
        inventory.setStock(5);
        branchInventoryRepository.save(inventory);

        assertThatThrownBy(() -> branchService.delete(created.getId()))
                .isInstanceOf(InvalidOperationException.class);

        branchService.deactivate(created.getId());

        Branch persisted = branchRepository.findById(created.getId()).orElseThrow();
        assertThat(persisted.getActive()).isFalse();
        assertThat(branchInventoryRepository.findByBranchId(created.getId())).isNotEmpty();
    }
}
