package com.supermarket.transferservice.controller.internal;

import com.supermarket.transferservice.repository.StockTransferRepository;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/transfers")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalTransferController {

    private final StockTransferRepository stockTransferRepository;

    @GetMapping("/branches/{branchId}/exists")
    public boolean existsForBranch(@PathVariable Long branchId) {
        return stockTransferRepository.existsBySourceBranchIdOrTargetBranchId(branchId, branchId);
    }
}
