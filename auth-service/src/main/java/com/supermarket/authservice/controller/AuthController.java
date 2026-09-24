package com.supermarket.authservice.controller;

import com.supermarket.authservice.dto.auth.AuthResponse;
import com.supermarket.authservice.dto.auth.LoginRequest;
import com.supermarket.authservice.event.AuthEventPublisher;
import com.supermarket.authservice.model.user.User;
import com.supermarket.authservice.service.security.AuthService;
import com.supermarket.authservice.service.security.JwtService;
import com.supermarket.authservice.service.security.TokenBlacklistService;
import com.supermarket.authservice.service.security.TokenValidationResult;
import com.supermarket.authservice.service.security.TokenValidationService;
import com.supermarket.commons.security.UserHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication")
public class AuthController {

    public static final String AUTH_ERROR_HEADER = "X-Auth-Error";

    private final AuthService authService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtService jwtService;
    private final TokenValidationService tokenValidationService;
    private final AuthEventPublisher authEventPublisher;

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate token")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No token provided"));
        }
        String token = authHeader.substring(7);
        try {
            String username = jwtService.getUsername(token);
            Date expiration = jwtService.getExpirationDate(token);
            tokenBlacklistService.blacklistToken(
                    token,
                    expiration.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
            );
            authEventPublisher.loggedOut(username);
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid token"));
        }
    }

    @GetMapping("/validate")
    @Operation(summary = "Validate a token and return the caller identity as headers (used by the API gateway)")
    public ResponseEntity<Void> validate(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized("Invalid or expired token");
        }
        TokenValidationResult result = tokenValidationService.validate(authHeader.substring(7));
        if (!result.valid()) {
            return unauthorized(result.error());
        }
        User user = result.user();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(UserHeaders.USER_ID, String.valueOf(user.getId()))
                .header(UserHeaders.EMAIL, user.getEmail())
                .header(UserHeaders.USERNAME, user.getUsername())
                .header(UserHeaders.ROLE, user.getRole().name());
        if (user.getBranchId() != null) {
            response.header(UserHeaders.BRANCH_ID, String.valueOf(user.getBranchId()));
        }
        return response.build();
    }

    private ResponseEntity<Void> unauthorized(String reason) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(AUTH_ERROR_HEADER, reason)
                .build();
    }
}
