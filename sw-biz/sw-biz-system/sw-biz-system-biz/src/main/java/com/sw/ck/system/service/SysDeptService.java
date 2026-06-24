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
}
