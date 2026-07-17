package com.sw.ck.system.service;

import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseService;
import com.sw.ck.system.entity.SysPost;

/**
 * 岗位 Service。
 */
public interface SysPostService extends BaseService<SysPost> {

    /**
     * 创建岗位。
     */
    Long create(SysPost post);

    /**
     * 更新岗位。
     */
    void update(SysPost post);

    /**
     * 删除岗位（逻辑删除）。
     */
    void delete(Long id);

    /**
     * 分页查询岗位。
     */
    PageResult<SysPost> page(PageParam pageParam, SysPost query);
}
