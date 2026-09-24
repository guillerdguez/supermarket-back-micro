package com.supermarket.salesservice.unit.service;

import com.supermarket.commons.exception.InsufficientPermissionsException;
import com.supermarket.commons.exception.InsufficientStockException;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.InvalidSaleStateException;
import com.supermarket.commons.exception.RemoteServiceException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.commons.security.CurrentUserProvider;
import com.supermarket.salesservice.client.BranchRemoteService;
import com.supermarket.salesservice.client.CatalogRemoteService;
import com.supermarket.salesservice.client.StockItem;
import com.supermarket.salesservice.client.StockMovementResponse;
import com.supermarket.salesservice.dto.sale.SaleRequest;
import com.supermarket.salesservice.dto.sale.SaleResponse;
import com.supermarket.salesservice.event.SalesEventPublisher;
import com.supermarket.salesservice.fixtures.branch.BranchFixtures;
import com.supermarket.salesservice.fixtures.cashregister.CashRegisterFixtures;
import com.supermarket.salesservice.fixtures.product.ProductFixtures;
import com.supermarket.salesservice.fixtures.sale.SaleFixtures;
import com.supermarket.salesservice.fixtures.user.UserFixtures;
import com.supermarket.salesservice.mapper.PaymentMapper;
import com.supermarket.salesservice.mapper.SaleDetailMapper;
import com.supermarket.salesservice.mapper.SaleMapper;
import com.supermarket.salesservice.model.sale.Payment;
import com.supermarket.salesservice.model.sale.Sale;
import com.supermarket.salesservice.model.sale.SaleStatus;
import com.supermarket.salesservice.repository.PaymentRepository;
import com.supermarket.salesservice.repository.SaleRepository;
import com.supermarket.salesservice.service.business.CashRegisterService;
import com.supermarket.salesservice.service.business.impl.SaleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private CashRegisterService cashRegisterService;
    @Mock
    private BranchRemoteService branchRemoteService;
    @Mock
    private CatalogRemoteService catalogRemoteService;
    @Mock
    private SalesEventPublisher salesEventPublisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private SaleServiceImpl saleService;

    @BeforeEach
    void setUp() {
        SaleMapper saleMapper = new SaleMapper(new SaleDetailMapper(), new PaymentMapper());
        saleService = new SaleServiceImpl(saleRepository, paymentRepository, saleMapper, currentUserProvider,
                cashRegisterService, branchRemoteService, catalogRemoteService, salesEventPublisher,
                transactionManager);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(UserFixtures.defaultCashier());
    }

    private void givenHappyPathDependencies() {
        given(branchRemoteService.requireBranch(1L)).willReturn(BranchFixtures.defaultBranch());
        given(cashRegisterService.getRegisterEntityByBranch(1L)).willReturn(CashRegisterFixtures.openRegister());
        given(catalogRemoteService.requireProducts(Set.of(1L))).willReturn(Map.of(1L, ProductFixtures.defaultProduct()));
    }

    @Test
    @DisplayName("CREATE - should price from catalog, reduce stock with an idempotency key and persist the sale")
    void create_ShouldCreateSaleAndReduceStock() {
        givenHappyPathDependencies();
        given(branchRemoteService.decreaseStock(eq(1L), anyString(), anyList()))
                .willReturn(new StockMovementResponse(1L, "k", "DECREASE", true, List.of()));
        given(saleRepository.save(any(Sale.class))).willAnswer(inv -> {
            Sale sale = inv.getArgument(0);
            sale.setId(100L);
            return sale;
        });
        given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

        SaleResponse response = saleService.create(SaleFixtures.validSaleRequest());

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getTotal()).isEqualByComparingTo("12.50");
        assertThat(response.getBranchName()).isEqualTo("Central Branch");
        assertThat(response.getCreatedByUsername()).isEqualTo("cashier-test");
        assertThat(response.getDetails()).singleElement()
                .satisfies(detail -> assertThat(detail.getProductName()).isEqualTo("Premium Rice"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        then(branchRemoteService).should().decreaseStock(eq(1L), key.capture(), eq(List.of(new StockItem(1L, 5))));
        assertThat(key.getValue()).startsWith("sale-").endsWith("-decrease");
        then(salesEventPublisher).should().saleCompleted(any(Sale.class));
    }

    @Test
    @DisplayName("CREATE - should reduce stock before persisting, never the other way round")
    void create_ShouldReduceStockBeforeSaving() {
        givenHappyPathDependencies();
        given(saleRepository.save(any(Sale.class))).willAnswer(inv -> inv.getArgument(0));
        given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

        saleService.create(SaleFixtures.validSaleRequest());

        InOrder order = inOrder(catalogRemoteService, branchRemoteService, saleRepository);
        order.verify(catalogRemoteService).requireProducts(Set.of(1L));
        order.verify(branchRemoteService).decreaseStock(eq(1L), anyString(), anyList());
        order.verify(saleRepository).save(any(Sale.class));
    }

    @Test
    @DisplayName("CREATE - should throw exception when branch is inactive")
    void create_WhenBranchInactive_ShouldThrow() {
        given(branchRemoteService.requireBranch(1L)).willReturn(BranchFixtures.inactiveBranch());

        assertThatThrownBy(() -> saleService.create(SaleFixtures.validSaleRequest()))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("inactive branch");
        then(branchRemoteService).should(never()).decreaseStock(anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("CREATE - should reject an amount below the total before touching any stock")
    void create_WhenAmountBelowTotal_ShouldThrowWithoutReducingStock() {
        givenHappyPathDependencies();
        SaleRequest request = SaleFixtures.validSaleRequest();
        request.setAmount(new BigDecimal("10.00"));

        assertThatThrownBy(() -> saleService.create(request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("does not cover");
        then(branchRemoteService).should(never()).decreaseStock(anyLong(), anyString(), anyList());
        then(saleRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("CREATE - should reject an amount above the total before touching any stock")
    void create_WhenAmountAboveTotal_ShouldThrowWithoutReducingStock() {
        givenHappyPathDependencies();
        SaleRequest request = SaleFixtures.validSaleRequest();
        request.setAmount(new BigDecimal("20.00"));

        assertThatThrownBy(() -> saleService.create(request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("exceeds");
        then(branchRemoteService).should(never()).decreaseStock(anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("CREATE - should not create any sale when branch-service reports insufficient stock")
    void create_WhenStockInsufficient_ShouldThrow() {
        givenHappyPathDependencies();
        given(branchRemoteService.decreaseStock(eq(1L), anyString(), anyList()))
                .willThrow(new InsufficientStockException("Insufficient stock for product 'Premium Rice'"));

        assertThatThrownBy(() -> saleService.create(SaleFixtures.validSaleRequest()))
                .isInstanceOf(InsufficientStockException.class);
        then(saleRepository).should(never()).save(any());
        then(branchRemoteService).should(never()).increaseStock(anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("CREATE - should fail fast without creating anything when branch-service is unavailable")
    void create_WhenBranchServiceUnavailable_ShouldThrow() {
        givenHappyPathDependencies();
        given(branchRemoteService.decreaseStock(eq(1L), anyString(), anyList()))
                .willThrow(new RemoteServiceException("branch-service", "branch-service is temporarily unavailable"));

        assertThatThrownBy(() -> saleService.create(SaleFixtures.validSaleRequest()))
                .isInstanceOf(RemoteServiceException.class);
        then(saleRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("CREATE - should give the stock back when the sale cannot be persisted after reducing it")
    void create_WhenPersistenceFails_ShouldCompensateStock() {
        givenHappyPathDependencies();
        given(saleRepository.save(any(Sale.class))).willThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> saleService.create(SaleFixtures.validSaleRequest()))
                .isInstanceOf(DataAccessResourceFailureException.class);

        ArgumentCaptor<String> decreaseKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> compensationKey = ArgumentCaptor.forClass(String.class);
        then(branchRemoteService).should().decreaseStock(eq(1L), decreaseKey.capture(), anyList());
        then(branchRemoteService).should().increaseStock(eq(1L), compensationKey.capture(),
                eq(List.of(new StockItem(1L, 5))));
        assertThat(compensationKey.getValue())
                .isEqualTo(decreaseKey.getValue().replace("-decrease", "-compensation"));
        then(salesEventPublisher).should(never()).saleCompleted(any());
    }

    @Test
    @DisplayName("CREATE - should throw exception when branch not found")
    void create_WhenBranchNotFound_ShouldThrow() {
        given(branchRemoteService.requireBranch(1L)).willThrow(new ResourceNotFoundException("Branch not found"));

        assertThatThrownBy(() -> saleService.create(SaleFixtures.validSaleRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Branch not found");
    }

    @Test
    @DisplayName("CREATE - should throw exception when product not found")
    void create_WhenProductNotFound_ShouldThrow() {
        given(branchRemoteService.requireBranch(1L)).willReturn(BranchFixtures.defaultBranch());
        given(cashRegisterService.getRegisterEntityByBranch(1L)).willReturn(CashRegisterFixtures.openRegister());
        given(catalogRemoteService.requireProducts(Set.of(1L)))
                .willThrow(new ResourceNotFoundException("Products not found with IDs: [1]"));

        assertThatThrownBy(() -> saleService.create(SaleFixtures.validSaleRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("[1]");
        then(branchRemoteService).should(never()).decreaseStock(anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("CREATE - should throw exception when no open cash register exists")
    void create_WhenNoOpenRegister_ShouldThrow() {
        given(branchRemoteService.requireBranch(1L)).willReturn(BranchFixtures.defaultBranch());
        given(cashRegisterService.getRegisterEntityByBranch(1L))
                .willThrow(new ResourceNotFoundException("No open register found for branch 1"));

        assertThatThrownBy(() -> saleService.create(SaleFixtures.validSaleRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        then(catalogRemoteService).should(never()).requireProducts(any());
    }

    @Test
    @DisplayName("CANCEL - should restore stock with a deterministic key and cancel the sale")
    void cancel_ShouldCancelSaleAndRestoreStock() {
        Sale sale = SaleFixtures.saleWithDetails();
        given(currentUserProvider.getCurrentUser()).willReturn(UserFixtures.defaultManager());
        given(saleRepository.findWithDetailsById(100L)).willReturn(Optional.of(sale));
        given(saleRepository.save(sale)).willReturn(sale);

        SaleResponse response = saleService.cancel(100L, SaleFixtures.validCancelRequest());

        assertThat(response.getStatus()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(response.getCancelledByUsername()).isEqualTo("manager-test");
        then(branchRemoteService).should().increaseStock(1L, "sale-100-cancel", List.of(new StockItem(1L, 5)));
        then(salesEventPublisher).should().saleCancelled(sale);
    }

    @Test
    @DisplayName("CANCEL - should keep the sale registered when the stock cannot be restored")
    void cancel_WhenStockRestoreFails_ShouldNotCancel() {
        Sale sale = SaleFixtures.saleWithDetails();
        given(saleRepository.findWithDetailsById(100L)).willReturn(Optional.of(sale));
        given(branchRemoteService.increaseStock(anyLong(), anyString(), anyList()))
                .willThrow(new RemoteServiceException("branch-service", "branch-service is temporarily unavailable"));

        assertThatThrownBy(() -> saleService.cancel(100L, SaleFixtures.validCancelRequest()))
                .isInstanceOf(RemoteServiceException.class);
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.REGISTERED);
        then(saleRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("CANCEL - should throw exception when sale is already cancelled")
    void cancel_WhenAlreadyCancelled_ShouldThrow() {
        Sale sale = SaleFixtures.saleWithDetails();
        sale.setStatus(SaleStatus.CANCELLED);
        given(saleRepository.findWithDetailsById(100L)).willReturn(Optional.of(sale));

        assertThatThrownBy(() -> saleService.cancel(100L, SaleFixtures.validCancelRequest()))
                .isInstanceOf(InvalidSaleStateException.class);
        then(branchRemoteService).should(never()).increaseStock(anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("CANCEL - should throw exception when sale not found")
    void cancel_WhenNotFound_ShouldThrow() {
        given(saleRepository.findWithDetailsById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> saleService.cancel(999L, SaleFixtures.validCancelRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("GET BY ID - should return sale")
    void getById_ShouldReturnSale() {
        given(saleRepository.findWithDetailsById(100L)).willReturn(Optional.of(SaleFixtures.saleWithDetails()));
        given(paymentRepository.findBySaleId(100L)).willReturn(List.of());

        SaleResponse response = saleService.getById(100L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getCreatedByEmail()).isEqualTo("cashier@test.com");
    }

    @Test
    @DisplayName("GET BY ID - should throw exception when not found")
    void getById_WhenNotFound_ShouldThrow() {
        given(saleRepository.findWithDetailsById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> saleService.getById(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("GET SALES BY CASHIER - should return cashier sales")
    void getSalesByCashier_ShouldReturnSales() {
        given(saleRepository.findByCreatedById(eq(1L), any())).willReturn(List.of(SaleFixtures.saleWithDetails()));
        given(paymentRepository.findBySaleIdIn(List.of(100L))).willReturn(List.of());

        assertThat(saleService.getSalesByCashier(1L)).hasSize(1);
    }

    @Test
    @DisplayName("GET SALE BY ID AND CASHIER - should return sale when owner")
    void getSaleByIdAndCashier_WhenOwner_ShouldReturn() {
        given(saleRepository.findWithDetailsById(100L)).willReturn(Optional.of(SaleFixtures.saleWithDetails()));
        given(paymentRepository.findBySaleId(100L)).willReturn(List.of());

        assertThat(saleService.getSaleByIdAndCashier(100L, 1L).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("GET SALE BY ID AND CASHIER - should throw exception when not owner")
    void getSaleByIdAndCashier_WhenNotOwner_ShouldThrow() {
        given(saleRepository.findWithDetailsById(100L)).willReturn(Optional.of(SaleFixtures.saleWithDetails()));

        assertThatThrownBy(() -> saleService.getSaleByIdAndCashier(100L, 99L))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("GET SALE BY ID AND CASHIER - should throw exception when createdBy is null")
    void getSaleByIdAndCashier_WhenCreatorMissing_ShouldThrow() {
        Sale sale = SaleFixtures.saleWithDetails();
        sale.setCreatedById(null);
        given(saleRepository.findWithDetailsById(100L)).willReturn(Optional.of(sale));

        assertThatThrownBy(() -> saleService.getSaleByIdAndCashier(100L, 1L))
                .isInstanceOf(InsufficientPermissionsException.class);
    }
}
