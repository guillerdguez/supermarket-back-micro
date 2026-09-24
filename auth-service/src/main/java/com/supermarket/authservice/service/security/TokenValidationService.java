package com.supermarket.authservice.service.security;

import com.supermarket.authservice.model.user.User;
import com.supermarket.authservice.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenValidationService {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public TokenValidationResult validate(String token) {
        try {
            if (tokenBlacklistService.isBlacklisted(token)) {
                return TokenValidationResult.rejected("Token invalidated via logout");
            }
            String email = jwtService.getUsername(token);
            Optional<User> user = userRepository.findByEmail(email);
            if (user.isEmpty()) {
                return TokenValidationResult.rejected("Invalid token");
            }
            if (!Boolean.TRUE.equals(user.get().getActive())) {
                return TokenValidationResult.rejected("Account disabled");
            }
            return TokenValidationResult.accepted(user.get());
        } catch (ExpiredJwtException e) {
            return TokenValidationResult.rejected("Token expired");
        } catch (Exception e) {
            log.debug("Token rejected: {}", e.getMessage());
            return TokenValidationResult.rejected("Invalid token");
        }
    }
}
