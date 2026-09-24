package com.supermarket.authservice.service.business;

import com.supermarket.authservice.dto.auth.UserResponse;
import com.supermarket.authservice.dto.user.ChangePasswordRequest;
import com.supermarket.authservice.dto.user.ProfileUpdateRequest;
import com.supermarket.authservice.dto.user.RoleUpdateRequest;
import com.supermarket.authservice.dto.user.UserRequest;
import com.supermarket.authservice.model.user.UserRole;

import java.util.List;

public interface UserManagementService {
    List<UserResponse> getAll(String username, String email, UserRole role);

    UserResponse getById(Long id);

    UserResponse create(UserRequest request);

    UserResponse update(Long id, UserRequest request);

    UserResponse updateRole(Long id, RoleUpdateRequest request);

    void delete(Long id);

    UserResponse activate(Long id);

    UserResponse getProfile();

    UserResponse updateProfile(ProfileUpdateRequest request);

    void changePassword(ChangePasswordRequest request);
}