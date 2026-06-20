package com.sw.ck.security.spi;

import com.sw.ck.security.holder.LoginUser;

/**
 * 由 sw-module-system 实现：security 框架本身不持有用户数据，仅定义加载契约。
 */
public interface UserDetailsProvider {

    LoginUser loadByUsername(String username);

    LoginUser loadByUserId(Long userId);
}
