package com.sw.ck.common.datascope;

import java.util.List;

/**
 * 部门树查询 SPI：DEPT_AND_CHILD 范围需要拿到某部门的全部下级部门 ID，但部门数据归属
 * sw-biz-system，本模块（sw-common）不查库，只定义契约，由 sw-biz-system 实现并注册为 Bean。
 * <p>
 * 未提供实现时装载 {@code noopDeptScopeProvider} 兜底 Bean（见
 * {@code com.sw.ck.common.config.mybatis.MybatisPlusConfig}），调用即抛出
 * {@link UnsupportedOperationException}——DEPT_AND_CHILD 范围在 system 模块实现部门树查询前
 * 不可用，避免静默放行或静默返回错误数据。
 */
public interface DeptScopeProvider {

    /**
     * 返回 deptId 的全部下级部门 ID，不含 deptId 自身。
     */
    List<Long> listChildDeptIds(Long deptId);
}
