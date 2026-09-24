package com.supermarket.salesservice.fixtures.cashregister;

import com.supermarket.salesservice.dto.cashregister.CashRegisterResponse;
import com.supermarket.salesservice.dto.cashregister.CloseRegisterRequest;
import com.supermarket.salesservice.dto.cashregister.OpenRegisterRequest;
import com.supermarket.salesservice.fixtures.branch.BranchFixtures;
import com.supermarket.salesservice.fixtures.user.UserFixtures;
import com.supermarket.salesservice.client.BranchSummary;
import com.supermarket.salesservice.model.cashregister.CashRegister;
import com.supermarket.salesservice.model.cashregister.CashRegisterStatus;
import com.supermarket.commons.security.AuthenticatedUser;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@UtilityClass
public class CashRegisterFixtures {

    public static CashRegister openRegister() {
        return openRegister(1L, BranchFixtures.defaultBranch(), UserFixtures.defaultCashier());
    }

    public static CashRegister openRegister(Long id, BranchSummary branch, AuthenticatedUser openedBy) {
        return CashRegister.builder()
                .id(id)
                .branchId(branch.id())
                .branchName(branch.name())
                .openingBalance(new BigDecimal("100.00"))
                .openingTime(LocalDateTime.now())
                .status(CashRegisterStatus.OPEN)
                .openedById(openedBy.id())
                .openedByUsername(openedBy.username())
                .build();
    }

    public static CashRegister closedRegister() {
        return closedRegister(1L, BranchFixtures.defaultBranch(),
                UserFixtures.defaultCashier(), UserFixtures.defaultManager());
    }

    public static CashRegister closedRegister(Long id, BranchSummary branch, AuthenticatedUser openedBy,
                                              AuthenticatedUser closedBy) {
        return CashRegister.builder()
                .id(id)
                .branchId(branch.id())
                .branchName(branch.name())
                .openingBalance(new BigDecimal("100.00"))
                .closingBalance(new BigDecimal("150.00"))
                .openingTime(LocalDateTime.now().minusHours(8))
                .closingTime(LocalDateTime.now())
                .status(CashRegisterStatus.CLOSED)
                .openedById(openedBy.id())
                .openedByUsername(openedBy.username())
                .closedById(closedBy.id())
                .closedByUsername(closedBy.username())
                .build();
    }

    public static OpenRegisterRequest validOpenRegisterRequest() {
        return OpenRegisterRequest.builder()
                .branchId(1L)
                .openingBalance(new BigDecimal("100.00"))
                .build();
    }

    public static CloseRegisterRequest validCloseRegisterRequest() {
        return CloseRegisterRequest.builder()
                .closingBalance(new BigDecimal("150.00"))
                .build();
    }

    public static CashRegisterResponse openRegisterResponse() {
        return CashRegisterResponse.builder()
                .id(1L)
                .branchId(1L)
                .branchName("Central Warehouse")
                .openingBalance(new BigDecimal("100.00"))
                .openingTime(LocalDateTime.now())
                .status(CashRegisterStatus.OPEN)
                .openedById(1L)
                .openedByUsername("cashier-test")
                .build();
    }

    public static CashRegisterResponse closedRegisterResponse() {
        return CashRegisterResponse.builder()
                .id(1L)
                .branchId(1L)
                .branchName("Central Warehouse")
                .openingBalance(new BigDecimal("100.00"))
                .closingBalance(new BigDecimal("150.00"))
                .openingTime(LocalDateTime.now().minusHours(8))
                .closingTime(LocalDateTime.now())
                .status(CashRegisterStatus.CLOSED)
                .openedById(1L)
                .openedByUsername("cashier-test")
                .closedById(2L)
                .closedByUsername("manager-test")
                .build();
    }
}