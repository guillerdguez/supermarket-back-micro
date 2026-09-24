package com.supermarket.commons.security;

public record AuthenticatedUser(Long id, String email, String username, String role, Long branchId) {

    public boolean hasRole(String expectedRole) {
        return role != null && role.equalsIgnoreCase(expectedRole);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
