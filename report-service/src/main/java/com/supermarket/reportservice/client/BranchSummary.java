package com.supermarket.reportservice.client;

public record BranchSummary(Long id, String name, String address, Boolean isWarehouse, Boolean active) {
}
