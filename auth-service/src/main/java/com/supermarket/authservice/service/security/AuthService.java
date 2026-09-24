package com.supermarket.authservice.service.security;

import com.supermarket.authservice.dto.auth.AuthResponse;
import com.supermarket.authservice.dto.auth.LoginRequest;
import com.supermarket.authservice.event.AuthEventPublisher;
import com.supermarket.authservice.exception.RateLimitExceededException;
import com.supermarket.authservice.mapper.UserResponseMapper;
import com.supermarket.authservice.model.user.User;
import com.supermarket.authservice.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RateLimitService rateLimitService;
    private final AuthEventPublisher authEventPublisher;
    private final UserResponseMapper userResponseMapper;

    public AuthResponse login(LoginRequest request) {
        String rateLimitKey = "login:" + request.getEmail();
        rateLimitService.checkRateLimit(rateLimitKey);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
            User user = securityUser.getUser();
            String token = jwtService.generateToken(securityUser);
            rateLimitService.resetRateLimit(rateLimitKey);
            authEventPublisher.loginSucceeded(user.getEmail());
            log.info("User logged in successfully: {}", user.getEmail());
            return AuthResponse.builder()
                    .token(token)
                    .user(userResponseMapper.toResponse(user))
                    .build();
        } catch (RateLimitExceededException e) {
            authEventPublisher.loginFailed(request.getEmail(), "Rate limit exceeded");
            throw e;
        } catch (Exception e) {
            authEventPublisher.loginFailed(request.getEmail(), "Invalid credentials");
            throw e;
        }
    }
}
