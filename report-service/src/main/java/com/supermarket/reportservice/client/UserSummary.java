package com.supermarket.reportservice.client;

public record UserSummary(Long id, String username, String email, String role, Long branchId, Boolean active) {
}
