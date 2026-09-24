package com.supermarket.authservice.fixtures.user;

import com.supermarket.authservice.model.user.User;
import com.supermarket.authservice.model.user.UserRole;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserFixtures {

    public static User defaultCashier() {
        return User.builder()
                .id(1L)
                .username("cashier-test")
                .email("cashier@test.com")
                .firstName("John")
                .lastName("Cashier")
                .role(UserRole.CASHIER)
                .active(true)
                .branchId(1L)
                .build();
    }

    public static User cashierWithoutBranch() {
        User cashier = defaultCashier();
        cashier.setBranchId(null);
        return cashier;
    }

    public static User defaultManager() {
        return User.builder()
                .id(2L)
                .username("manager-test")
                .email("manager@test.com")
                .firstName("Jane")
                .lastName("Manager")
                .role(UserRole.MANAGER)
                .active(true)
                .build();
    }

    public static User defaultAdmin() {
        return User.builder()
                .id(3L)
                .username("admin-test")
                .email("admin@test.com")
                .firstName("Admin")
                .lastName("System")
                .role(UserRole.ADMIN)
                .active(true)
                .build();
    }
}