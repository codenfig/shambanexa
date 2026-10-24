package com.shambanexa.common;

import org.springframework.http.HttpStatus;

public final class TenantContext {

    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(CurrentUser user) {
        CURRENT.set(user);
    }

    public static CurrentUser require() {
        CurrentUser user = CURRENT.get();
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Sign in to continue");
        }
        return user;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
