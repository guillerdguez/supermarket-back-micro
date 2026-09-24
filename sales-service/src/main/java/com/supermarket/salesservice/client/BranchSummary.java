package com.supermarket.salesservice.client;

public record BranchSummary(Long id, String name, String address, Boolean isWarehouse, Boolean active) {
}
