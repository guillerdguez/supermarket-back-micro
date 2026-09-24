package com.supermarket.apigateway.security;

import java.util.List;

public final class UserHeaders {
    public static final String USER_ID = "X-User-Id";
    public static final String EMAIL = "X-User-Email";
    public static final String USERNAME = "X-User-Name";
    public static final String ROLE = "X-User-Role";
    public static final String BRANCH_ID = "X-User-Branch-Id";
    public static final String AUTH_ERROR = "X-Auth-Error";

    public static final List<String> IDENTITY = List.of(USER_ID, EMAIL, USERNAME, ROLE, BRANCH_ID);

    private UserHeaders() {
    }
}
