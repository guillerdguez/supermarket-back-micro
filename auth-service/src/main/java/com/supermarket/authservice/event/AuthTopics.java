package com.supermarket.authservice.event;

public final class AuthTopics {
    public static final String LOGIN_SUCCESS = "auth.login.success";
    public static final String LOGIN_FAILED = "auth.login.failed";
    public static final String LOGOUT = "auth.logout";

    private AuthTopics() {
    }
}
