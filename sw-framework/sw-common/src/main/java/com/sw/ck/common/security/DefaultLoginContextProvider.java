package com.sw.ck.common.security;

import com.sw.ck.common.datascope.DataScopeType;

import java.util.Set;

/**
 * 无登录态场景（系统初始化、定时任务等）下的兜底实现，始终返回 null，
 * 由调用方（如 {@link com.sw.ck.common.config.mybatis.CommonMetaObjectHandler}）决定降级值。
 * <p>
 * 仅当 sw-security 未被引入或其实现未生效时才会装载（见 MybatisPlusConfig 的 @ConditionalOnMissingBean）。
 */
public class DefaultLoginContextProvider implements LoginContextProvider {

    @Override
    public Long getUserId() {
        return null;
    }

    @Override
    public Long getTenantId() {
        return null;
    }

    @Override
    public Long getDeptId() {
        return null;
    }

    @Override
    public DataScopeType getDataScopeType() {
        return DataScopeType.ALL;
    }

    @Override
    public Set<Long> getCustomDeptIds() {
        return Set.of();
    }

    @Override
    public boolean isSuperAdmin() {
        return false;
    }
}
