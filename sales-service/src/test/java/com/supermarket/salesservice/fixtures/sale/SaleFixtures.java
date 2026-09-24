package com.supermarket.salesservice.fixtures.sale;

import com.supermarket.salesservice.dto.sale.CancelSaleRequest;
import com.supermarket.salesservice.dto.sale.SaleRequest;
import com.supermarket.salesservice.dto.sale.SaleResponse;
import com.supermarket.salesservice.dto.saleDetail.SaleDetailRequest;
import com.supermarket.salesservice.dto.saleDetail.SaleDetailResponse;
import com.supermarket.salesservice.fixtures.branch.BranchFixtures;
import com.supermarket.salesservice.fixtures.cashregister.CashRegisterFixtures;
import com.supermarket.salesservice.fixtures.product.ProductFixtures;
import com.supermarket.salesservice.fixtures.user.UserFixtures;
import com.supermarket.salesservice.client.BranchSummary;
import com.supermarket.salesservice.model.cashregister.CashRegister;
import com.supermarket.salesservice.model.cashregister.CashRegisterStatus;
import com.supermarket.salesservice.client.ProductSummary;
import com.supermarket.salesservice.model.sale.Sale;
import com.supermarket.salesservice.model.sale.PaymentType;
import com.supermarket.salesservice.model.sale.SaleDetail;
import com.supermarket.salesservice.model.sale.SaleStatus;
import com.supermarket.commons.security.AuthenticatedUser;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class SaleFixtures {

    public static SaleRequest validSaleRequest() {
        return SaleRequest.builder()
                .branchId(1L)
                .date(LocalDate.now())
                .details(List.of(
                        SaleDetailRequest.builder()
                                .productId(1L)
                                .quantity(5)
                                .build()))
                .amount(new BigDecimal("12.50"))
                .paymentType(PaymentType.CASH)
                .build();
    }

    public static SaleRequest invalidSaleRequest() {
        return SaleRequest.builder()
                .branchId(null)
                .details(List.of())
                .build();
    }

    public static CancelSaleRequest validCancelRequest() {
        return CancelSaleRequest.builder()
                .reason("Customer returned items")
                .build();
    }

    public static CancelSaleRequest invalidCancelRequest() {
        return CancelSaleRequest.builder()
                .reason("")
                .build();
    }

    public static Sale saleWithDetails() {
        BranchSummary branch = BranchFixtures.defaultBranch();
        ProductSummary product = ProductFixtures.defaultProduct();
        AuthenticatedUser cashier = UserFixtures.defaultCashier();
        CashRegister cashRegister = CashRegisterFixtures.openRegister();

        Sale sale = Sale.builder()
                .id(100L)
                .date(LocalDate.now())
                .total(new BigDecimal("12.50"))
                .status(SaleStatus.REGISTERED)
                .branchId(branch.id())
                .branchName(branch.name())
                .createdById(cashier.id())
                .createdByUsername(cashier.username())
                .createdByEmail(cashier.email())
                .createdAt(LocalDateTime.now())
                .cashRegister(cashRegister)
                .details(new ArrayList<>())
                .build();

        SaleDetail detail = SaleDetail.builder()
                .quantity(5)
                .price(new BigDecimal("2.50"))
                .productId(product.id())
                .productName(product.name())
                .productCategory(product.category())
                .sale(sale)
                .build();

        sale.getDetails().add(detail);
        return sale;
    }

    public static SaleResponse saleResponse() {
        return SaleResponse.builder()
                .id(100L)
                .total(new BigDecimal("12.50"))
                .date(LocalDate.now())
                .status(SaleStatus.REGISTERED)
                .branchId(1L)
                .branchName("Central Branch")
                .createdById(1L)
                .createdByUsername("cashier-test")
                .createdByEmail("cashier@test.com")
                .createdAt(LocalDateTime.now())
                .cashRegisterId(1L)
                .cashRegisterStatus(CashRegisterStatus.OPEN)
                .details(List.of(
                        SaleDetailResponse.builder()
                                .productName("Premium Rice")
                                .quantity(5)
                                .unitPrice(new BigDecimal("2.50"))
                                .subtotal(new BigDecimal("12.50"))
                                .build()))
                .build();
    }

    public static SaleResponse cancelledSaleResponse() {
        return SaleResponse.builder()
                .id(100L)
                .total(new BigDecimal("12.50"))
                .date(LocalDate.now())
                .status(SaleStatus.CANCELLED)
                .branchId(1L)
                .branchName("Central Branch")
                .createdById(1L)
                .createdByUsername("cashier-test")
                .createdByEmail("cashier@test.com")
                .createdAt(LocalDateTime.now())
                .cancelledById(2L)
                .cancelledByUsername("manager-test")
                .cancellationReason("Customer returned items")
                .cancelledAt(LocalDateTime.now())
                .cashRegisterId(1L)
                .cashRegisterStatus(CashRegisterStatus.OPEN)
                .details(List.of(
                        SaleDetailResponse.builder()
                                .productName("Premium Rice")
                                .quantity(5)
                                .unitPrice(new BigDecimal("2.50"))
                                .subtotal(new BigDecimal("12.50"))
                                .build()))
                .build();
    }
}