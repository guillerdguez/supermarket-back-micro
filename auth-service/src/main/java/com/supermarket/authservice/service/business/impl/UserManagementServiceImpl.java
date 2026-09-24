package com.supermarket.authservice.service.business.impl;

import com.supermarket.authservice.dto.auth.UserResponse;
import com.supermarket.authservice.dto.user.ChangePasswordRequest;
import com.supermarket.authservice.dto.user.ProfileUpdateRequest;
import com.supermarket.authservice.dto.user.RoleUpdateRequest;
import com.supermarket.authservice.dto.user.UserRequest;
import com.supermarket.commons.exception.DuplicateResourceException;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.authservice.model.user.User;
import com.supermarket.authservice.model.user.UserRole;
import com.supermarket.authservice.client.BranchLookupService;
import com.supermarket.authservice.mapper.UserResponseMapper;
import com.supermarket.authservice.repository.UserRepository;
import com.supermarket.commons.security.CurrentUserProvider;
import com.supermarket.authservice.service.business.UserManagementService;
import com.supermarket.authservice.specification.UserSpecifications;
import com.supermarket.authservice.validator.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final BranchLookupService branchLookupService;
    private final UserResponseMapper userResponseMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAll(String username, String email, UserRole role) {
        log.info("Fetching all users with filters");
        Specification<User> spec = UserSpecifications.withFilters(username, email, role);
        return userResponseMapper.toResponseList(userRepository.findAll(spec, Sort.by("username")));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        log.info("Fetching user with ID: {}", id);
        return toResponse(findUser(id));
    }

    @Override
    public UserResponse create(UserRequest request) {
        log.info("Creating new user: {}", request.getEmail());
        List<String> passwordErrors = passwordValidator.validatePassword(request.getPassword());
        if (!passwordErrors.isEmpty()) {
            throw new IllegalArgumentException("Password validation failed: " + String.join(", ", passwordErrors));
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }
        requireBranchForCashier(request.getRole(), request.getBranchId());
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(request.getRole())
                .active(true)
                .branchId(validateBranchOrNull(request.getBranchId()))
                .build();
        User saved = userRepository.save(user);
        log.info("User created successfully: {}", saved.getEmail());
        return toResponse(saved);
    }

    @Override
    public UserResponse update(Long id, UserRequest request) {
        log.info("Updating user with ID: {}", id);
        User user = findUser(id);
        if (!user.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }
        if (!user.getUsername().equals(request.getUsername()) && userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }
        requireBranchForCashier(request.getRole(), request.getBranchId());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(request.getRole());
        user.setBranchId(validateBranchOrNull(request.getBranchId()));
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            List<String> passwordErrors = passwordValidator.validatePassword(request.getPassword());
            if (!passwordErrors.isEmpty()) {
                throw new IllegalArgumentException("Password validation failed: " + String.join(", ", passwordErrors));
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse updateRole(Long id, RoleUpdateRequest request) {
        log.info("Updating role for user ID: {} to {}", id, request.getRole());
        User user = findUser(id);
        if (request.getRole() == UserRole.CASHIER && user.getBranchId() == null) {
            throw new InvalidOperationException("Cannot set role to CASHIER: user has no branch assigned");
        }
        user.setRole(request.getRole());
        return toResponse(userRepository.save(user));
    }

    @Override
    public void delete(Long id) {
        log.info("Deactivating user with ID: {}", id);
        User user = findUser(id);
        if (user.getRole() == UserRole.ADMIN && user.getActive()
                && userRepository.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new InvalidOperationException("Cannot deactivate the last active ADMIN user");
        }
        user.setActive(false);
        userRepository.save(user);
        log.info("User deactivated successfully - ID: {}", id);
    }

    @Override
    public UserResponse activate(Long id) {
        log.info("Activating user with ID: {}", id);
        User user = findUser(id);
        if (user.getRole() == UserRole.CASHIER && user.getBranchId() == null) {
            throw new InvalidOperationException("Cannot activate: CASHIER user has no branch assigned");
        }
        user.setActive(true);
        User saved = userRepository.save(user);
        log.info("User activated successfully - ID: {}", id);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getProfile() {
        return toResponse(getCurrentUser());
    }

    @Override
    public UserResponse updateProfile(ProfileUpdateRequest request) {
        log.info("Updating profile for current user");
        User user = getCurrentUser();
        if (!user.getUsername().equals(request.getUsername()) && userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }
        user.setUsername(request.getUsername());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        boolean canEditSensitive = user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER;
        if (canEditSensitive) {
            if (!user.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Email already registered: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
            user.setBranchId(validateBranchOrNull(request.getBranchId()));
        }
        return toResponse(userRepository.save(user));
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        log.info("Changing password for current user");
        User user = getCurrentUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidOperationException("Current password is incorrect");
        }
        List<String> passwordErrors = passwordValidator.validatePassword(request.getNewPassword());
        if (!passwordErrors.isEmpty()) {
            throw new IllegalArgumentException("Password validation failed: " + String.join(", ", passwordErrors));
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user: {}", user.getEmail());
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
    }

    private void requireBranchForCashier(UserRole role, Long branchId) {
        if (role == UserRole.CASHIER && branchId == null) {
            throw new InvalidOperationException("Branch is required for CASHIER users");
        }
    }

    private Long validateBranchOrNull(Long branchId) {
        if (branchId == null) {
            return null;
        }
        branchLookupService.requireBranch(branchId);
        return branchId;
    }

    private User getCurrentUser() {
        Long currentUserId = currentUserProvider.getCurrentUser().id();
        return findUser(currentUserId);
    }

    private UserResponse toResponse(User user) {
        return userResponseMapper.toResponse(user);
    }
}
