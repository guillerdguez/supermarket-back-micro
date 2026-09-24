package com.supermarket.authservice.dto.internal;

public record UserSummary(Long id, String username, String email, String role, Long branchId, Boolean active) {
}
