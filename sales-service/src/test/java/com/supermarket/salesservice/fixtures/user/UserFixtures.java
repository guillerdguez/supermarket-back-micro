package com.supermarket.salesservice.fixtures.user;

import com.supermarket.commons.security.AuthenticatedUser;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserFixtures {

    public static AuthenticatedUser defaultCashier() {
        return new AuthenticatedUser(1L, "cashier@test.com", "cashier-test", "CASHIER", 1L);
    }

    public static AuthenticatedUser cashierWithoutBranch() {
        return new AuthenticatedUser(1L, "cashier@test.com", "cashier-test", "CASHIER", null);
    }

    public static AuthenticatedUser defaultManager() {
        return new AuthenticatedUser(2L, "manager@test.com", "manager-test", "MANAGER", null);
    }

    public static AuthenticatedUser defaultAdmin() {
        return new AuthenticatedUser(3L, "admin@test.com", "admin-test", "ADMIN", null);
    }
}
