package com.sw.ck.common.security;

import com.sw.ck.common.datascope.DataScopeType;

import java.util.Set;

/**
 * 登录上下文 SPI。
 * <p>
 * sw-security 依赖 sw-common（单向依赖，禁止反向),
 * 因此 sw-common 不能直接引用 sw-security 的 LoginUserHolder，
 * 改由本接口解耦：sw-security 提供基于 LoginUserHolder 的实现并覆盖 {@link DefaultLoginContextProvider}。
 */
public interface LoginContextProvider {

    /**
     * 当前登录用户 ID，取不到登录态（未登录、系统任务等）时返回 null。
     */
    Long getUserId();

    /**
     * 当前租户 ID，取不到登录态时返回 null。
     */
    Long getTenantId();

    /**
     * 当前登录人所属部门 ID，取不到登录态时返回 null。
     */
    Long getDeptId();

    /**
     * 当前登录人的数据范围，取不到登录态时返回 {@link DataScopeType#ALL}（无登录态场景如系统
     * 任务不做数据范围限制，与 {@link #getTenantId()} 降级为超管租户的语义一致）。
     */
    DataScopeType getDataScopeType();

    /**
     * {@link #getDataScopeType()} == CUSTOM 时生效的自定义可见部门集合，取不到登录态时返回空集合。
     */
    Set<Long> getCustomDeptIds();

    /**
     * 是否超管。超管短路一切数据范围限制。
     */
    boolean isSuperAdmin();
}
