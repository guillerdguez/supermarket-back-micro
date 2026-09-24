package com.supermarket.authservice.mapper;

import com.supermarket.authservice.client.BranchLookupService;
import com.supermarket.authservice.dto.auth.UserResponse;
import com.supermarket.authservice.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserResponseMapper {

    private final BranchLookupService branchLookupService;

    public UserResponse toResponse(User user) {
        Map<Long, String> names = user.getBranchId() != null
                ? branchLookupService.branchNames(Set.of(user.getBranchId()))
                : Map.of();
        return toResponse(user, names);
    }

    public List<UserResponse> toResponseList(List<User> users) {
        Set<Long> branchIds = users.stream()
                .map(User::getBranchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = branchLookupService.branchNames(branchIds);
        return users.stream().map(user -> toResponse(user, names)).toList();
    }

    private UserResponse toResponse(User user, Map<Long, String> branchNames) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .active(user.getActive())
                .branchId(user.getBranchId())
                .branchName(user.getBranchId() != null ? branchNames.get(user.getBranchId()) : null)
                .build();
    }
}
