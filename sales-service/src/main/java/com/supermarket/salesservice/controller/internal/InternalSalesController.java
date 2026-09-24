package com.supermarket.salesservice.controller.internal;

import com.supermarket.salesservice.dto.internal.BranchSalesUsage;
import com.supermarket.salesservice.repository.CashRegisterRepository;
import com.supermarket.salesservice.repository.SaleRepository;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/sales")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalSalesController {

    private final SaleRepository saleRepository;
    private final CashRegisterRepository cashRegisterRepository;

    @GetMapping("/branches/{branchId}/usage")
    public BranchSalesUsage getBranchUsage(@PathVariable Long branchId) {
        return new BranchSalesUsage(
                saleRepository.existsByBranchId(branchId),
                cashRegisterRepository.existsByBranchId(branchId));
    }

    @GetMapping("/products/{productId}/in-use")
    public boolean isProductInUse(@PathVariable Long productId) {
        return saleRepository.existsByDetailsProductId(productId);
    }
}
