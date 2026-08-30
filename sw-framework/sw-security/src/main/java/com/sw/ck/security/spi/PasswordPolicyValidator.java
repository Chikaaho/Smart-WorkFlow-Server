package com.sw.ck.security.spi;

/**
 * 密码策略扩展点（长度/复杂度/历史密码等规则）。本模块不内置任何实现、不在任何地方调用，
 * 仅预留接口；不满足策略时由实现方自行抛出业务异常，本接口不规定具体异常类型。
 */
public interface PasswordPolicyValidator {

    void validate(String rawPassword);
}
