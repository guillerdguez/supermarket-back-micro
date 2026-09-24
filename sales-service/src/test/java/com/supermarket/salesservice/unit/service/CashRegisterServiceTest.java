package com.supermarket.salesservice.unit.service;

import com.supermarket.salesservice.dto.cashregister.CashRegisterResponse;
import com.supermarket.salesservice.dto.cashregister.CloseRegisterRequest;
import com.supermarket.salesservice.dto.cashregister.OpenRegisterRequest;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.salesservice.fixtures.branch.BranchFixtures;
import com.supermarket.salesservice.fixtures.cashregister.CashRegisterFixtures;
import com.supermarket.salesservice.fixtures.user.UserFixtures;
import com.supermarket.salesservice.mapper.CashRegisterMapper;
import com.supermarket.salesservice.client.BranchRemoteService;
import com.supermarket.salesservice.client.BranchSummary;
import com.supermarket.salesservice.model.cashregister.CashRegister;
import com.supermarket.salesservice.model.cashregister.CashRegisterStatus;
import com.supermarket.commons.security.AuthenticatedUser;
import com.supermarket.commons.security.CurrentUserProvider;
import com.supermarket.salesservice.repository.CashRegisterRepository;
import com.supermarket.salesservice.repository.SaleRepository;
import com.supermarket.salesservice.event.SalesEventPublisher;
import com.supermarket.salesservice.service.business.impl.CashRegisterServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CashRegisterServiceTest {

    @Mock
    private CashRegisterRepository cashRegisterRepository;
    @Mock
    private BranchRemoteService branchRemoteService;
    @Mock
    private SaleRepository saleRepository;
    @Mock
    private CashRegisterMapper cashRegisterMapper;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private SalesEventPublisher salesEventPublisher;

    @InjectMocks
    private CashRegisterServiceImpl cashRegisterService;

    private AuthenticatedUser mockUser;
    private BranchSummary branch;

    @BeforeEach
    void setUp() {
        mockUser = UserFixtures.defaultCashier();
        branch = BranchFixtures.defaultBranch();
    }

    @Test
    void openRegister_shouldCreateNewOpenRegister() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        OpenRegisterRequest request = new OpenRegisterRequest(branch.id(), new BigDecimal("100.00"));

        CashRegister savedRegister = CashRegisterFixtures.openRegister(100L, branch, mockUser);
        CashRegisterResponse response = CashRegisterResponse.builder().id(100L).build();

        given(branchRemoteService.requireBranch(branch.id())).willReturn(branch);
        given(cashRegisterRepository.findByBranchIdAndStatus(branch.id(), CashRegisterStatus.OPEN))
                .willReturn(Optional.empty());
        given(cashRegisterRepository.save(any(CashRegister.class))).willReturn(savedRegister);
        given(cashRegisterMapper.toResponse(savedRegister)).willReturn(response);

        CashRegisterResponse result = cashRegisterService.openRegister(request);

        assertThat(result.getId()).isEqualTo(100L);
        then(cashRegisterRepository).should().save(any(CashRegister.class));
    }

    @Test
    void openRegister_whenBranchNotFound_shouldThrowException() {
        OpenRegisterRequest request = new OpenRegisterRequest(99L, new BigDecimal("100.00"));
        given(branchRemoteService.requireBranch(99L)).willThrow(new ResourceNotFoundException("Branch not found"));

        assertThatThrownBy(() -> cashRegisterService.openRegister(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void openRegister_whenBranchInactive_shouldThrowException() {
        BranchSummary inactive = BranchFixtures.inactiveBranch();
        OpenRegisterRequest request = new OpenRegisterRequest(inactive.id(), new BigDecimal("100.00"));
        given(branchRemoteService.requireBranch(inactive.id())).willReturn(inactive);

        assertThatThrownBy(() -> cashRegisterService.openRegister(request))
                .isInstanceOf(InvalidOperationException.class);

        then(cashRegisterRepository).should(never()).save(any(CashRegister.class));
    }

    @Test
    void openRegister_whenBranchIdOmitted_shouldUseBranchAssignedToUser() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        Long assignedBranchId = mockUser.branchId();
        given(branchRemoteService.requireBranch(assignedBranchId)).willReturn(branch);
        OpenRegisterRequest request = new OpenRegisterRequest(null, new BigDecimal("100.00"));

        CashRegister savedRegister = CashRegisterFixtures.openRegister(100L, branch, mockUser);
        CashRegisterResponse response = CashRegisterResponse.builder().id(100L).build();

        given(cashRegisterRepository.findByBranchIdAndStatus(assignedBranchId, CashRegisterStatus.OPEN))
                .willReturn(Optional.empty());
        given(cashRegisterRepository.save(any(CashRegister.class))).willReturn(savedRegister);
        given(cashRegisterMapper.toResponse(savedRegister)).willReturn(response);

        CashRegisterResponse result = cashRegisterService.openRegister(request);

        assertThat(result.getId()).isEqualTo(100L);
        then(cashRegisterRepository).should().save(argThat(reg -> reg.getBranchId().equals(assignedBranchId)));
        then(branchRemoteService).should().requireBranch(assignedBranchId);
    }

    @Test
    void openRegister_whenBranchIdOmittedAndUserHasNoBranch_shouldThrowException() {
        given(currentUserProvider.getCurrentUser()).willReturn(UserFixtures.cashierWithoutBranch());
        OpenRegisterRequest request = new OpenRegisterRequest(null, new BigDecimal("100.00"));

        assertThatThrownBy(() -> cashRegisterService.openRegister(request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("no branch assigned");
    }

    @Test
    void openRegister_whenRegisterAlreadyOpen_shouldThrowException() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        OpenRegisterRequest request = new OpenRegisterRequest(branch.id(), new BigDecimal("100.00"));

        given(branchRemoteService.requireBranch(branch.id())).willReturn(branch);
        given(cashRegisterRepository.findByBranchIdAndStatus(branch.id(), CashRegisterStatus.OPEN))
                .willReturn(Optional.of(mock(CashRegister.class)));

        assertThatThrownBy(() -> cashRegisterService.openRegister(request))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void closeRegister_shouldCloseRegister() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        Long registerId = 100L;
        CloseRegisterRequest request = new CloseRegisterRequest(new BigDecimal("150.00"));

        CashRegister register = CashRegisterFixtures.openRegister(registerId, branch, mockUser);
        CashRegisterResponse response = CashRegisterResponse.builder().id(registerId).build();

        given(cashRegisterRepository.findById(registerId)).willReturn(Optional.of(register));
        given(saleRepository.sumTotalByCashRegisterId(registerId)).willReturn(new BigDecimal("50.00"));
        given(cashRegisterRepository.save(register)).willReturn(register);
        given(cashRegisterMapper.toResponse(register)).willReturn(response);

        CashRegisterResponse result = cashRegisterService.closeRegister(registerId, request);

        assertThat(result.getId()).isEqualTo(registerId);
        assertThat(register.getStatus()).isEqualTo(CashRegisterStatus.CLOSED);
        assertThat(register.getClosingBalance()).isEqualByComparingTo("150.00");
        assertThat(register.getClosedById()).isEqualTo(mockUser.id());
        assertThat(register.getClosedByUsername()).isEqualTo(mockUser.username());
        then(cashRegisterRepository).should().save(register);
        then(salesEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void closeRegister_whenRegisterNotFound_shouldThrowException() {
        given(cashRegisterRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cashRegisterService.closeRegister(99L, new CloseRegisterRequest(BigDecimal.ZERO)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void closeRegister_whenAlreadyClosed_shouldThrowException() {
        Long registerId = 100L;
        CashRegister register = CashRegisterFixtures.closedRegister();
        given(cashRegisterRepository.findById(registerId)).willReturn(Optional.of(register));

        assertThatThrownBy(() -> cashRegisterService.closeRegister(registerId, new CloseRegisterRequest(BigDecimal.ZERO)))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void closeRegister_whenDiscrepancy_shouldTriggerNotification() {
        given(currentUserProvider.getCurrentUser()).willReturn(mockUser);
        Long registerId = 100L;
        CloseRegisterRequest request = new CloseRegisterRequest(new BigDecimal("160.00"));

        CashRegister register = CashRegisterFixtures.openRegister(registerId, branch, mockUser);
        CashRegisterResponse response = CashRegisterResponse.builder().id(registerId).build();

        given(cashRegisterRepository.findById(registerId)).willReturn(Optional.of(register));
        given(saleRepository.sumTotalByCashRegisterId(registerId)).willReturn(new BigDecimal("50.00"));
        given(cashRegisterRepository.save(register)).willReturn(register);
        given(cashRegisterMapper.toResponse(register)).willReturn(response);

        cashRegisterService.closeRegister(registerId, request);

        then(salesEventPublisher).should().cashRegisterDiscrepancy(
                eq(register),
                argThat(variance -> variance.compareTo(new BigDecimal("10.00")) == 0)
        );
    }
}