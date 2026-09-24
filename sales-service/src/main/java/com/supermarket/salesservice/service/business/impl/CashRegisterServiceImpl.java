package com.supermarket.salesservice.service.business.impl;

import com.supermarket.commons.security.AuthenticatedUser;
import com.supermarket.commons.security.CurrentUserProvider;
import com.supermarket.salesservice.client.BranchRemoteService;
import com.supermarket.salesservice.client.BranchSummary;
import com.supermarket.salesservice.dto.cashregister.CashRegisterResponse;
import com.supermarket.salesservice.event.SalesEventPublisher;
import com.supermarket.salesservice.dto.cashregister.CloseRegisterRequest;
import com.supermarket.salesservice.dto.cashregister.OpenRegisterRequest;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.salesservice.mapper.CashRegisterMapper;
import com.supermarket.salesservice.model.cashregister.CashRegister;
import com.supermarket.salesservice.model.cashregister.CashRegisterStatus;
import com.supermarket.salesservice.repository.CashRegisterRepository;
import com.supermarket.salesservice.repository.SaleRepository;
import com.supermarket.salesservice.service.business.CashRegisterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CashRegisterServiceImpl implements CashRegisterService {
    private final CashRegisterRepository cashRegisterRepository;
    private final CashRegisterMapper cashRegisterMapper;
    private final CurrentUserProvider currentUserProvider;
    private final BranchRemoteService branchRemoteService;
    private final SalesEventPublisher salesEventPublisher;
    private final SaleRepository saleRepository;

    @Override
    public CashRegisterResponse openRegister(OpenRegisterRequest request) {
        AuthenticatedUser currentUser = getCurrentUser();
        BranchSummary branch = resolveBranch(request, currentUser);
        cashRegisterRepository.findByBranchIdAndStatus(branch.id(), CashRegisterStatus.OPEN)
                .ifPresent(reg -> {
                    throw new InvalidOperationException("There is already an open register for this branch");
                });
        CashRegister register = CashRegister.builder()
                .branchId(branch.id())
                .branchName(branch.name())
                .openingBalance(request.getOpeningBalance())
                .openingTime(LocalDateTime.now())
                .status(CashRegisterStatus.OPEN)
                .openedById(currentUser.id())
                .openedByUsername(currentUser.username())
                .build();
        return cashRegisterMapper.toResponse(cashRegisterRepository.save(register));
    }

    @Override
    public CashRegisterResponse closeRegister(Long registerId, CloseRegisterRequest request) {
        AuthenticatedUser currentUser = getCurrentUser();
        CashRegister register = cashRegisterRepository.findById(registerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash register not found"));
        if (register.getStatus() == CashRegisterStatus.CLOSED) {
            throw new InvalidOperationException("Register is already closed");
        }
        register.setClosingBalance(request.getClosingBalance());
        register.setClosingTime(LocalDateTime.now());
        register.setStatus(CashRegisterStatus.CLOSED);
        register.setClosedById(currentUser.id());
        register.setClosedByUsername(currentUser.username());
        CashRegister saved = cashRegisterRepository.save(register);
        notifyIfDiscrepancy(saved);
        return cashRegisterMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegisterResponse getCurrentRegisterByBranch(Long branchId) {
        CashRegister register = cashRegisterRepository.findByBranchIdAndStatus(branchId, CashRegisterStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("No open register found for branch " + branchId));
        return cashRegisterMapper.toResponse(register);
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegister getRegisterEntityByBranch(Long branchId) {
        return cashRegisterRepository.findByBranchIdAndStatus(branchId, CashRegisterStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("No open register found for branch " + branchId));
    }

    private AuthenticatedUser getCurrentUser() {
        return currentUserProvider.getCurrentUser();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashRegisterResponse> getAll() {
        log.info("Fetching all cash registers");
        return cashRegisterMapper.toResponseList(cashRegisterRepository.findAll());
    }

    private BranchSummary resolveBranch(OpenRegisterRequest request, AuthenticatedUser currentUser) {
        Long branchId = request.getBranchId() != null ? request.getBranchId() : currentUser.branchId();
        if (branchId == null) {
            throw new InvalidOperationException("This user has no branch assigned");
        }
        BranchSummary branch = branchRemoteService.requireBranch(branchId);
        if (!Boolean.TRUE.equals(branch.active())) {
            throw new InvalidOperationException("Cannot open a cash register in an inactive branch");
        }
        return branch;
    }

    private void notifyIfDiscrepancy(CashRegister register) {
        if (register.getClosingBalance() == null || register.getOpeningBalance() == null) return;
        BigDecimal totalSales = saleRepository.sumTotalByCashRegisterId(register.getId());
        BigDecimal expected = register.getOpeningBalance().add(totalSales);
        BigDecimal variance = register.getClosingBalance().subtract(expected);
        if (variance.abs().compareTo(BigDecimal.ZERO) > 0) {
            try {
                salesEventPublisher.cashRegisterDiscrepancy(register, variance);
            } catch (Exception e) {
                log.warn("Failed to send discrepancy notification for register {}: {}",
                        register.getId(), e.getMessage());
            }
        }
    }
}