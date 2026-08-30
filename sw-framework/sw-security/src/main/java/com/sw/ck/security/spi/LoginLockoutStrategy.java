package com.sw.ck.security.spi;

/**
 * 登录失败锁定扩展点：记录失败次数、判断是否锁定、登录成功后清零。本模块不内置任何实现、
 * 不在任何地方调用，仅预留接口；具体存储（Redis/DB）与锁定策略由实现方决定。
 */
public interface LoginLockoutStrategy {

    boolean isLocked(String username);

    void onLoginFailure(String username);

    void onLoginSuccess(String username);
}
