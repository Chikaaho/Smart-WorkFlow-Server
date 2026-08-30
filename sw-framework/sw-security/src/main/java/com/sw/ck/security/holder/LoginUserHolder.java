package com.sw.ck.security.holder;

/**
 * 登录上下文持有者。基于纯 {@link ThreadLocal}，不绑定任何 Web 请求/Servlet 上下文，
 * 因此定时任务、事件监听等无 Web 请求线程也可在执行前手动 {@link #set} 一个系统态 LoginUser
 * （或不 set，由 sw-common 的 {@code CommonMetaObjectHandler}/{@code CommonTenantLineHandler}
 * 在取不到时降级为系统兜底值），执行完毕后必须 {@link #clear}。
 * <p>
 * 注意：线程池中的线程会被复用，若不在 finally 中 clear，会导致下一个任务复用同一线程时
 * 读到上一个任务残留的 LoginUser，调用方（定时任务调度器、事件监听器等）必须自行保证
 * set/clear 成对出现。
 */
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
