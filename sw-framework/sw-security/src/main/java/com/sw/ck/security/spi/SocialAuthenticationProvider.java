package com.sw.ck.security.spi;

import com.sw.ck.security.holder.LoginUser;

/**
 * 第三方登录扩展点（企业微信/钉钉等）。{@link #type()} 标识具体渠道；实现方完成
 * OAuth/扫码等交互并完成用户绑定后返回 {@link LoginUser}。本模块不内置任何渠道实现、
 * 不在任何地方调用，仅预留接口。
 */
public interface SocialAuthenticationProvider {

    String type();

    LoginUser authenticate(String code);
}
