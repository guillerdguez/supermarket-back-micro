package com.supermarket.authservice.service.security;

import com.supermarket.authservice.model.user.User;

public record TokenValidationResult(User user, String error) {

    public static TokenValidationResult accepted(User user) {
        return new TokenValidationResult(user, null);
    }

    public static TokenValidationResult rejected(String error) {
        return new TokenValidationResult(null, error);
    }

    public boolean valid() {
        return user != null;
    }
}
