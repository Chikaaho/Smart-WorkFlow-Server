package com.sw.ck.security.spi;

/**
 * 验证码校验扩展点（图形验证码/短信验证码等）。本模块不内置任何实现、不在任何地方调用，
 * 仅预留接口；由账密登录 controller（system/auth 模块，后续 Prompt）在登录前自行校验。
 */
public interface CaptchaValidator {

    boolean validate(String captchaKey, String captchaCode);
}
