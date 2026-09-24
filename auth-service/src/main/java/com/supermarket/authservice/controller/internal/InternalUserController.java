package com.supermarket.authservice.controller.internal;

import com.supermarket.authservice.dto.internal.UserSummary;
import com.supermarket.authservice.model.user.User;
import com.supermarket.authservice.model.user.UserRole;
import com.supermarket.authservice.repository.UserRepository;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Hidden
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalUserController {

    private final UserRepository userRepository;

    @GetMapping
    public List<UserSummary> getByIds(@RequestParam("ids") List<Long> ids) {
        return userRepository.findAllById(ids).stream().map(this::toSummary).toList();
    }

    @GetMapping("/by-role")
    public List<UserSummary> getByRoles(@RequestParam("roles") List<UserRole> roles) {
        return userRepository.findByRoleIn(roles).stream().map(this::toSummary).toList();
    }

    @GetMapping("/branches/{branchId}/exists")
    public boolean existsByBranch(@PathVariable Long branchId) {
        return userRepository.existsByBranchId(branchId);
    }

    private UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole().name(), user.getBranchId(), user.getActive());
    }
}
