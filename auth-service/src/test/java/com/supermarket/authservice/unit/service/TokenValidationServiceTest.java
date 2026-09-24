package com.supermarket.authservice.unit.service;

import com.supermarket.authservice.fixtures.user.UserFixtures;
import com.supermarket.authservice.model.user.User;
import com.supermarket.authservice.repository.UserRepository;
import com.supermarket.authservice.service.security.JwtService;
import com.supermarket.authservice.service.security.TokenBlacklistService;
import com.supermarket.authservice.service.security.TokenValidationResult;
import com.supermarket.authservice.service.security.TokenValidationService;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TokenValidationServiceTest {

    private static final String TOKEN = "valid.jwt.token";
    private static final String EMAIL = "cashier@test.com";

    @Mock
    private JwtService jwtService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TokenValidationService tokenValidationService;

    @Test
    @DisplayName("should accept a valid token of an active user")
    void validate_ActiveUser_ShouldAccept() {
        User user = UserFixtures.defaultCashier();
        given(tokenBlacklistService.isBlacklisted(TOKEN)).willReturn(false);
        given(jwtService.getUsername(TOKEN)).willReturn(EMAIL);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        TokenValidationResult result = tokenValidationService.validate(TOKEN);

        assertThat(result.valid()).isTrue();
        assertThat(result.user()).isSameAs(user);
    }

    @Test
    @DisplayName("should reject a valid token when the account is disabled")
    void validate_DisabledUser_ShouldReject() {
        User user = UserFixtures.defaultCashier();
        user.setActive(false);
        given(tokenBlacklistService.isBlacklisted(TOKEN)).willReturn(false);
        given(jwtService.getUsername(TOKEN)).willReturn(EMAIL);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        TokenValidationResult result = tokenValidationService.validate(TOKEN);

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).isEqualTo("Account disabled");
    }

    @Test
    @DisplayName("should reject a blacklisted token without parsing it")
    void validate_BlacklistedToken_ShouldReject() {
        given(tokenBlacklistService.isBlacklisted(TOKEN)).willReturn(true);

        TokenValidationResult result = tokenValidationService.validate(TOKEN);

        assertThat(result.error()).isEqualTo("Token invalidated via logout");
        then(jwtService).should(never()).getUsername(TOKEN);
    }

    @Test
    @DisplayName("should reject an expired token")
    void validate_ExpiredToken_ShouldReject() {
        given(tokenBlacklistService.isBlacklisted(TOKEN)).willReturn(false);
        given(jwtService.getUsername(TOKEN)).willThrow(new ExpiredJwtException(null, null, "expired"));

        TokenValidationResult result = tokenValidationService.validate(TOKEN);

        assertThat(result.error()).isEqualTo("Token expired");
    }

    @Test
    @DisplayName("should reject a token whose subject no longer exists")
    void validate_UnknownUser_ShouldReject() {
        given(tokenBlacklistService.isBlacklisted(TOKEN)).willReturn(false);
        given(jwtService.getUsername(TOKEN)).willReturn(EMAIL);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        TokenValidationResult result = tokenValidationService.validate(TOKEN);

        assertThat(result.error()).isEqualTo("Invalid token");
    }
}
