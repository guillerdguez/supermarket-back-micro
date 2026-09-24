package com.supermarket.transferservice.client;

public record BranchSummary(Long id, String name, String address, Boolean isWarehouse, Boolean active) {
}
