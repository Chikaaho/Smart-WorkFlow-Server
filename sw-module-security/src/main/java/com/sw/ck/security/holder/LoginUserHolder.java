package com.sw.ck.security.holder;

public final class LoginUserHolder {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private LoginUserHolder() {
    }

    public static void set(LoginUser loginUser) {
        CONTEXT.set(loginUser);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
