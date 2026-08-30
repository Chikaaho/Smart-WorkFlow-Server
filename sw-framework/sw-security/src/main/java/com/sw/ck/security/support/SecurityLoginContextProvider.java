package com.sw.ck.security.support;

import com.sw.ck.common.datascope.DataScopeType;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;

import java.util.Set;

/**
 * 基于 {@link LoginUserHolder} 的登录上下文实现，覆盖 sw-common 的兜底空实现
 * （见 {@code MybatisPlusConfig#defaultLoginContextProvider} 上的 @ConditionalOnMissingBean）。
 */
public class SecurityLoginContextProvider implements LoginContextProvider {

    @Override
    public Long getUserId() {
        LoginUser loginUser = LoginUserHolder.get();
        return loginUser != null ? loginUser.getUserId() : null;
    }

    @Override
    public Long getTenantId() {
        LoginUser loginUser = LoginUserHolder.get();
        return loginUser != null ? loginUser.getTenantId() : null;
    }

    @Override
    public Long getDeptId() {
        LoginUser loginUser = LoginUserHolder.get();
        return loginUser != null ? loginUser.getDeptId() : null;
    }

    /**
     * 按枚举常量名做字符串映射：sw-security 的 {@link com.sw.ck.security.holder.DataScope} 与
     * sw-common 的 {@link DataScopeType} 常量名一一对应，但两个模块的单向依赖关系不允许
     * sw-common 直接引用 sw-security 的类型，故只能在这里转换，不能改成强类型映射。
     */
    @Override
    public DataScopeType getDataScopeType() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getDataScope() == null) {
            return DataScopeType.ALL;
        }
        return DataScopeType.valueOf(loginUser.getDataScope().name());
    }

    @Override
    public Set<Long> getCustomDeptIds() {
        LoginUser loginUser = LoginUserHolder.get();
        return loginUser != null && loginUser.getCustomDeptIds() != null ? loginUser.getCustomDeptIds() : Set.of();
    }

    @Override
    public boolean isSuperAdmin() {
        LoginUser loginUser = LoginUserHolder.get();
        return loginUser != null && loginUser.isSuperAdmin();
    }
}
