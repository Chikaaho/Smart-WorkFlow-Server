package com.sw.ck.system.service;

import com.sw.ck.common.service.BaseService;
import com.sw.ck.system.entity.SysDept;

import java.util.List;

/**
 * 部门 Service。
 */
public interface SysDeptService extends BaseService<SysDept> {

    /**
     * 创建部门。
     */
    Long create(SysDept dept);

    /**
     * 更新部门。
     */
    void update(SysDept dept);

    /**
     * 删除部门（逻辑删除）。
     * 删除前校验：无子部门且无在职用户方可删除。
     */
    void delete(Long id);

    /**
     * 查询部门树（返回全量列表，按 sort 升序）。
     */
    List<SysDept> listTree();

    /**
     * 条件查询部门树。
     * <p>
     * 支持部门名称包含匹配与状态（0=正常 1=停用）过滤，可单独或组合使用；
     * 结果只包含直接命中节点及其定位所需的祖先路径（沿 parentId 逐级上溯），
     * 去重后按 sort 升序稳定排序。无条件参数时与 {@link #listTree()} 行为完全一致。
     * 非法状态值（非 0/1）显式抛出 PARAM_ERROR，不会静默退化为全量查询。
     * </p>
     *
     * @param query 查询条件，可为 null（等价于无条件）
     */
    List<SysDept> listTree(DeptQuery query);
}
