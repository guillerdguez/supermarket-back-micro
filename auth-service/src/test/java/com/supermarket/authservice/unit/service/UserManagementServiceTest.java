package com.supermarket.authservice.unit.service;

import com.supermarket.authservice.dto.auth.UserResponse;
import com.supermarket.authservice.dto.user.ChangePasswordRequest;
import com.supermarket.authservice.dto.user.ProfileUpdateRequest;
import com.supermarket.authservice.dto.user.RoleUpdateRequest;
import com.supermarket.authservice.dto.user.UserRequest;
import com.supermarket.commons.exception.DuplicateResourceException;
import com.supermarket.commons.exception.InvalidOperationException;
import com.supermarket.commons.exception.ResourceNotFoundException;
import com.supermarket.authservice.fixtures.user.UserFixtures;
import com.supermarket.authservice.client.BranchLookupService;
import com.supermarket.authservice.client.BranchSummary;
import com.supermarket.authservice.mapper.UserResponseMapper;
import com.supermarket.authservice.model.user.User;
import com.supermarket.authservice.model.user.UserRole;
import com.supermarket.authservice.repository.UserRepository;
import com.supermarket.commons.security.AuthenticatedUser;
import com.supermarket.commons.security.CurrentUserProvider;
import com.supermarket.authservice.service.business.impl.UserManagementServiceImpl;
import com.supermarket.authservice.validator.PasswordValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BranchLookupService branchLookupService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordValidator passwordValidator;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private UserManagementServiceImpl userManagementService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = UserFixtures.defaultAdmin();
        userManagementService = new UserManagementServiceImpl(
                userRepository, branchLookupService, new UserResponseMapper(branchLookupService),
                passwordEncoder, passwordValidator, currentUserProvider);
        lenient().when(branchLookupService.branchNames(any())).thenReturn(Map.of());
    }

    private void givenCurrentUser(User user) {
        given(currentUserProvider.getCurrentUser()).willReturn(new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getUsername(), user.getRole().name(), user.getBranchId()));
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
    }

    private BranchSummary branchSummary() {
        return new BranchSummary(1L, "Central Warehouse", "Main Street 1", true, true);
    }

    @Test
    @DisplayName("GET BY ID - should return user")
    void getById_ShouldReturnUser() {
        given(userRepository.findById(3L)).willReturn(Optional.of(mockUser));

        UserResponse result = userManagementService.getById(3L);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(mockUser.getEmail());
    }

    @Test
    @DisplayName("GET BY ID - should throw when not found")
    void getById_WhenNotFound_ShouldThrow() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userManagementService.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("CREATE - should save user when data is unique")
    void create_WhenUnique_ShouldSave() {
        UserRequest request = UserRequest.builder()
                .username("newuser").email("new@test.com").password("Password1!")
                .firstName("New").lastName("User").role(UserRole.ADMIN).build();

        given(passwordValidator.validatePassword(request.getPassword())).willReturn(List.of());
        given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(userRepository.existsByUsername(request.getUsername())).willReturn(false);
        given(passwordEncoder.encode(request.getPassword())).willReturn("encoded");
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserResponse result = userManagementService.create(request);

        assertThat(result).isNotNull();
        then(userRepository).should().save(any(User.class));
    }

    @Test
    @DisplayName("CREATE - should throw when role is CASHIER and branch is missing")
    void create_WhenCashierWithoutBranch_ShouldThrow() {
        UserRequest request = UserRequest.builder()
                .username("newuser").email("new@test.com").password("Password1!")
                .firstName("New").lastName("User").role(UserRole.CASHIER).build();

        given(passwordValidator.validatePassword(request.getPassword())).willReturn(List.of());
        given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(userRepository.existsByUsername(request.getUsername())).willReturn(false);

        assertThatThrownBy(() -> userManagementService.create(request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Branch is required");
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("CREATE - should assign the requested branch")
    void create_WithBranch_ShouldAssignBranch() {
        BranchSummary branch = branchSummary();
        UserRequest request = UserRequest.builder()
                .username("cashier2").email("cashier2@test.com").password("Password1!")
                .firstName("New").lastName("Cashier").role(UserRole.CASHIER)
                .branchId(branch.id()).build();

        given(passwordValidator.validatePassword(request.getPassword())).willReturn(List.of());
        given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(userRepository.existsByUsername(request.getUsername())).willReturn(false);
        given(passwordEncoder.encode(request.getPassword())).willReturn("encoded");
        given(branchLookupService.requireBranch(branch.id())).willReturn(branch);
        given(branchLookupService.branchNames(any())).willReturn(Map.of(branch.id(), branch.name()));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        UserResponse result = userManagementService.create(request);

        assertThat(result.getBranchId()).isEqualTo(branch.id());
        assertThat(result.getBranchName()).isEqualTo(branch.name());
    }

    @Test
    @DisplayName("CREATE - should throw when branch does not exist")
    void create_WhenBranchNotFound_ShouldThrow() {
        UserRequest request = UserRequest.builder()
                .username("cashier2").email("cashier2@test.com").password("Password1!")
                .firstName("New").lastName("Cashier").role(UserRole.CASHIER)
                .branchId(999L).build();

        given(passwordValidator.validatePassword(request.getPassword())).willReturn(List.of());
        given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(userRepository.existsByUsername(request.getUsername())).willReturn(false);
        given(passwordEncoder.encode(request.getPassword())).willReturn("encoded");
        given(branchLookupService.requireBranch(999L)).willThrow(new ResourceNotFoundException("Branch not found with ID: 999"));

        assertThatThrownBy(() -> userManagementService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    @DisplayName("CREATE - should throw when email already exists")
    void create_WhenEmailExists_ShouldThrow() {
        UserRequest request = UserRequest.builder()
                .username("newuser").email("existing@test.com").password("Password1!")
                .firstName("New").lastName("User").role(UserRole.CASHIER).build();

        given(passwordValidator.validatePassword(request.getPassword())).willReturn(List.of());
        given(userRepository.existsByEmail(request.getEmail())).willReturn(true);

        assertThatThrownBy(() -> userManagementService.create(request))
                .isInstanceOf(DuplicateResourceException.class);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("CREATE - should throw when username already exists")
    void create_WhenUsernameExists_ShouldThrow() {
        UserRequest request = UserRequest.builder()
                .username("taken").email("new@test.com").password("Password1!")
                .firstName("New").lastName("User").role(UserRole.CASHIER).build();

        given(passwordValidator.validatePassword(request.getPassword())).willReturn(List.of());
        given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(userRepository.existsByUsername(request.getUsername())).willReturn(true);

        assertThatThrownBy(() -> userManagementService.create(request))
                .isInstanceOf(DuplicateResourceException.class);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("CREATE - should throw when password is invalid")
    void create_WhenPasswordInvalid_ShouldThrow() {
        UserRequest request = UserRequest.builder()
                .username("newuser").email("new@test.com").password("weak")
                .firstName("New").lastName("User").role(UserRole.CASHIER).build();

        given(passwordValidator.validatePassword(request.getPassword()))
                .willReturn(List.of("Password must contain at least one uppercase letter"));

        assertThatThrownBy(() -> userManagementService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password validation failed");
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("UPDATE ROLE - should update user role")
    void updateRole_ShouldUpdateRole() {
        User user = UserFixtures.defaultCashier();
        RoleUpdateRequest request = new RoleUpdateRequest(UserRole.MANAGER);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        UserResponse result = userManagementService.updateRole(1L, request);

        assertThat(user.getRole()).isEqualTo(UserRole.MANAGER);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("UPDATE ROLE - should throw when user not found")
    void updateRole_WhenNotFound_ShouldThrow() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userManagementService.updateRole(999L, new RoleUpdateRequest(UserRole.MANAGER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("UPDATE ROLE - should throw when promoting to CASHIER without a branch")
    void updateRole_WhenPromotingToCashierWithoutBranch_ShouldThrow() {
        User user = UserFixtures.defaultManager();

        given(userRepository.findById(2L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userManagementService.updateRole(2L, new RoleUpdateRequest(UserRole.CASHIER)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("no branch assigned");
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("UPDATE - should throw when role is CASHIER and branch is missing")
    void update_WhenCashierWithoutBranch_ShouldThrow() {
        User user = UserFixtures.defaultCashier();
        UserRequest request = UserRequest.builder()
                .username(user.getUsername()).email(user.getEmail()).password("Password1!")
                .firstName(user.getFirstName()).lastName(user.getLastName()).role(UserRole.CASHIER).build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userManagementService.update(1L, request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Branch is required");
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("DELETE - should deactivate user")
    void delete_ShouldDeactivateUser() {
        User user = UserFixtures.defaultCashier();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        userManagementService.delete(1L);

        assertThat(user.getActive()).isFalse();
        then(userRepository).should().save(user);
    }

    @Test
    @DisplayName("DELETE - should throw when user not found")
    void delete_WhenNotFound_ShouldThrow() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userManagementService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("DELETE - should throw when deactivating the last active admin")
    void delete_WhenLastActiveAdmin_ShouldThrow() {
        User admin = UserFixtures.defaultAdmin();

        given(userRepository.findById(3L)).willReturn(Optional.of(admin));
        given(userRepository.countByRoleAndActiveTrue(UserRole.ADMIN)).willReturn(1L);

        assertThatThrownBy(() -> userManagementService.delete(3L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("last active ADMIN");
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("DELETE - should deactivate admin when another active admin remains")
    void delete_WhenAnotherActiveAdminExists_ShouldDeactivate() {
        User admin = UserFixtures.defaultAdmin();

        given(userRepository.findById(3L)).willReturn(Optional.of(admin));
        given(userRepository.countByRoleAndActiveTrue(UserRole.ADMIN)).willReturn(2L);
        given(userRepository.save(admin)).willReturn(admin);

        userManagementService.delete(3L);

        assertThat(admin.getActive()).isFalse();
        then(userRepository).should().save(admin);
    }

    @Test
    @DisplayName("ACTIVATE - should activate a user with a branch")
    void activate_ShouldActivateUser() {
        User user = UserFixtures.defaultCashier();
        user.setActive(false);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        userManagementService.activate(1L);

        assertThat(user.getActive()).isTrue();
        then(userRepository).should().save(user);
    }

    @Test
    @DisplayName("ACTIVATE - should throw when CASHIER has no branch assigned")
    void activate_WhenCashierWithoutBranch_ShouldThrow() {
        User user = UserFixtures.cashierWithoutBranch();
        user.setActive(false);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userManagementService.activate(1L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("no branch assigned");
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("ACTIVATE - should throw when user not found")
    void activate_WhenNotFound_ShouldThrow() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userManagementService.activate(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("GET PROFILE - should return current user profile")
    void getProfile_ShouldReturnCurrentUser() {
        givenCurrentUser(mockUser);

        UserResponse result = userManagementService.getProfile();

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(mockUser.getEmail());
    }

    @Test
    @DisplayName("UPDATE PROFILE - should update username and names")
    void updateProfile_ShouldUpdateFields() {
        givenCurrentUser(mockUser);

        ProfileUpdateRequest request = new ProfileUpdateRequest("newusername", "NewFirst", "NewLast", mockUser.getEmail(), null);
        given(userRepository.existsByUsername("newusername")).willReturn(false);
        given(userRepository.save(mockUser)).willReturn(mockUser);

        userManagementService.updateProfile(request);

        assertThat(mockUser.getUsername()).isEqualTo("newusername");
        assertThat(mockUser.getFirstName()).isEqualTo("NewFirst");
        assertThat(mockUser.getLastName()).isEqualTo("NewLast");
    }

    @Test
    @DisplayName("UPDATE PROFILE - should throw when username already taken")
    void updateProfile_WhenUsernameTaken_ShouldThrow() {
        givenCurrentUser(mockUser);

        ProfileUpdateRequest request = new ProfileUpdateRequest("taken", "First", "Last", mockUser.getEmail(), null);
        given(userRepository.existsByUsername("taken")).willReturn(true);

        assertThatThrownBy(() -> userManagementService.updateProfile(request))
                .isInstanceOf(DuplicateResourceException.class);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("UPDATE PROFILE - ADMIN should be able to change own email and branch")
    void updateProfile_AsAdmin_ShouldUpdateEmailAndBranch() {
        givenCurrentUser(mockUser);
        BranchSummary branch = branchSummary();

        ProfileUpdateRequest request = new ProfileUpdateRequest("admin-test", "Admin", "System", "new-admin@test.com", branch.id());
        given(userRepository.existsByEmail("new-admin@test.com")).willReturn(false);
        given(branchLookupService.requireBranch(branch.id())).willReturn(branch);
        given(branchLookupService.branchNames(any())).willReturn(Map.of(branch.id(), branch.name()));
        given(userRepository.save(mockUser)).willReturn(mockUser);

        userManagementService.updateProfile(request);

        assertThat(mockUser.getEmail()).isEqualTo("new-admin@test.com");
        assertThat(mockUser.getBranchId()).isEqualTo(branch.id());
    }

    @Test
    @DisplayName("UPDATE PROFILE - CASHIER should not be able to change email or branch")
    void updateProfile_AsCashier_ShouldIgnoreEmailAndBranch() {
        User cashier = UserFixtures.defaultCashier();
        Long originalBranch = cashier.getBranchId();
        givenCurrentUser(cashier);

        ProfileUpdateRequest request = new ProfileUpdateRequest("cashier-test", "John", "Cashier", "hacked@test.com", 999L);
        given(userRepository.save(cashier)).willReturn(cashier);

        userManagementService.updateProfile(request);

        assertThat(cashier.getEmail()).isEqualTo("cashier@test.com");
        assertThat(cashier.getBranchId()).isEqualTo(originalBranch);
        then(userRepository).should(never()).existsByEmail(any());
        then(branchLookupService).should(never()).requireBranch(any());
    }

    @Test
    @DisplayName("CHANGE PASSWORD - should update password when current is correct")
    void changePassword_WhenCurrentCorrect_ShouldUpdate() {
        givenCurrentUser(mockUser);

        ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!", "NewPass1!");
        given(passwordEncoder.matches("OldPass1!", mockUser.getPassword())).willReturn(true);
        given(passwordValidator.validatePassword("NewPass1!")).willReturn(List.of());
        given(passwordEncoder.encode("NewPass1!")).willReturn("newEncoded");
        given(userRepository.save(mockUser)).willReturn(mockUser);

        userManagementService.changePassword(request);

        then(userRepository).should().save(mockUser);
    }

    @Test
    @DisplayName("CHANGE PASSWORD - should throw when current password is wrong")
    void changePassword_WhenCurrentWrong_ShouldThrow() {
        givenCurrentUser(mockUser);

        ChangePasswordRequest request = new ChangePasswordRequest("WrongPass!", "NewPass1!");
        given(passwordEncoder.matches("WrongPass!", mockUser.getPassword())).willReturn(false);

        assertThatThrownBy(() -> userManagementService.changePassword(request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Current password is incorrect");
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("CHANGE PASSWORD - should throw when new password is invalid")
    void changePassword_WhenNewPasswordInvalid_ShouldThrow() {
        givenCurrentUser(mockUser);

        ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!", "weak");
        given(passwordEncoder.matches("OldPass1!", mockUser.getPassword())).willReturn(true);
        given(passwordValidator.validatePassword("weak"))
                .willReturn(List.of("Password must contain at least one uppercase letter"));

        assertThatThrownBy(() -> userManagementService.changePassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password validation failed");
        then(userRepository).should(never()).save(any());
    }
}