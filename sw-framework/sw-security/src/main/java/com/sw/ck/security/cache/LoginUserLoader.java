package com.sw.ck.security.cache;

import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.spi.UserDetailsProvider;
import lombok.RequiredArgsConstructor;

/**
 * 登录上下文"装载 + 缓存"骨架：缓存命中直接返回；未命中才回查 {@link UserDetailsProvider}
 * 并写回缓存。Prompt 3 的认证流程（FilterChain）应通过本类获取 LoginUser，而不是直接
 * 调用 UserDetailsProvider，以保证缓存策略统一生效。
 */
@RequiredArgsConstructor
public class LoginUserLoader {

    private final UserDetailsProvider userDetailsProvider;
    private final LoginUserCacheService loginUserCacheService;

    public LoginUser loadByUserId(Long userId) {
        LoginUser cached = loginUserCacheService.get(userId);
        if (cached != null) {
            return cached;
        }
        LoginUser loginUser = userDetailsProvider.loadByUserId(userId);
        if (loginUser != null) {
            loginUserCacheService.cache(loginUser);
        }
        return loginUser;
    }

    /**
     * 踢人下线 / 权限变更立即生效：清掉缓存即可，token 本身无需作废，
     * 下一次请求会因缓存未命中重新回查最新数据。
     */
    public void kickOut(Long userId) {
        loginUserCacheService.evict(userId);
    }
}
