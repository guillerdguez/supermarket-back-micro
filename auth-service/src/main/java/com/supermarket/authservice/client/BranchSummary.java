package com.supermarket.authservice.client;

public record BranchSummary(Long id, String name, String address, Boolean isWarehouse, Boolean active) {
}
