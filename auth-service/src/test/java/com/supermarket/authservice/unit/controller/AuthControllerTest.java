package com.supermarket.authservice.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.supermarket.authservice.controller.AuthController;
import com.supermarket.authservice.dto.auth.LoginRequest;
import com.supermarket.authservice.exception.AuthExceptionHandler;
import com.supermarket.commons.web.CommonExceptionHandler;
import com.supermarket.authservice.exception.RateLimitExceededException;
import com.supermarket.authservice.fixtures.auth.AuthFixtures;
import com.supermarket.authservice.event.AuthEventPublisher;
import com.supermarket.authservice.fixtures.user.UserFixtures;
import com.supermarket.authservice.service.security.TokenValidationResult;
import com.supermarket.authservice.service.security.TokenValidationService;
import com.supermarket.authservice.service.security.AuthService;
import com.supermarket.authservice.service.security.JwtService;
import com.supermarket.authservice.service.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AuthService authService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenValidationService tokenValidationService;

    @Mock
    private AuthEventPublisher authEventPublisher;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new AuthExceptionHandler(), new CommonExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /auth/login - should return 200 with token")
    void login_ShouldReturn200() throws Exception {
        given(authService.login(any())).willReturn(AuthFixtures.authResponse());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.validLoginRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-value"))
                .andExpect(jsonPath("$.user.email").value("user@test.com"));
    }

    @Test
    @DisplayName("POST /auth/login - should return 400 when request is invalid")
    void login_InvalidRequest_ShouldReturn400() throws Exception {
        LoginRequest invalid = LoginRequest.builder()
                .email("not-an-email")
                .password("")
                .build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/login - should return 401 on bad credentials")
    void login_BadCredentials_ShouldReturn401() throws Exception {
        given(authService.login(any())).willThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.validLoginRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/login - should return 429 when rate limit is exceeded")
    void login_RateLimitExceeded_ShouldReturn429() throws Exception {
        given(authService.login(any())).willThrow(new RateLimitExceededException(60));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.validLoginRequest())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    @DisplayName("POST /auth/logout - should blacklist token and return 200")
    void logout_ShouldReturn200() throws Exception {
        given(jwtService.getUsername("valid-token")).willReturn("user@test.com");
        given(jwtService.getExpirationDate("valid-token")).willReturn(new Date(System.currentTimeMillis() + 60000));

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(tokenBlacklistService).blacklistToken(anyString(), any());
        verify(authEventPublisher).loggedOut("user@test.com");
    }

    @Test
    @DisplayName("POST /auth/logout - should return 400 when Authorization header has no Bearer prefix")
    void logout_MissingBearerPrefix_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "invalid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No token provided"));

        verify(tokenBlacklistService, never()).blacklistToken(anyString(), any());
    }

    @Test
    @DisplayName("POST /auth/logout - should return 400 when token is invalid")
    void logout_InvalidToken_ShouldReturn400() throws Exception {
        given(jwtService.getUsername("bad-token")).willThrow(new RuntimeException("Malformed token"));

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid token"));
    }

    @Test
    @DisplayName("GET /auth/validate - should return 200 with identity headers for a valid token")
    void validate_ValidToken_ShouldReturnIdentityHeaders() throws Exception {
        given(tokenValidationService.validate("valid-token"))
                .willReturn(TokenValidationResult.accepted(UserFixtures.defaultCashier()));

        mockMvc.perform(get("/auth/validate")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-User-Id", "1"))
                .andExpect(header().string("X-User-Email", "cashier@test.com"))
                .andExpect(header().string("X-User-Name", "cashier-test"))
                .andExpect(header().string("X-User-Role", "CASHIER"))
                .andExpect(header().string("X-User-Branch-Id", "1"));
    }

    @Test
    @DisplayName("GET /auth/validate - should return 401 with the rejection reason for an invalid token")
    void validate_RejectedToken_ShouldReturn401() throws Exception {
        given(tokenValidationService.validate("expired-token"))
                .willReturn(TokenValidationResult.rejected("Token expired"));

        mockMvc.perform(get("/auth/validate")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Auth-Error", "Token expired"))
                .andExpect(header().doesNotExist("X-User-Id"));
    }

    @Test
    @DisplayName("GET /auth/validate - should return 401 when no bearer token is sent")
    void validate_MissingToken_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/auth/validate"))
                .andExpect(status().isUnauthorized());

        verify(tokenValidationService, never()).validate(anyString());
    }
}
