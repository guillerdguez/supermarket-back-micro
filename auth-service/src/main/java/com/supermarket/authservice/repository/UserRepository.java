package com.supermarket.authservice.repository;

import com.supermarket.authservice.model.user.User;
import com.supermarket.authservice.model.user.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findByRoleIn(List<UserRole> roles);

    boolean existsByBranchId(Long branchId);

    long countByRoleAndActiveTrue(UserRole role);
}