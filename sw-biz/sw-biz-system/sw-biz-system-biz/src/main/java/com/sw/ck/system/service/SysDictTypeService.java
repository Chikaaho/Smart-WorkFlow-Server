package com.sw.ck.system.service;

import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseService;
import com.sw.ck.system.entity.SysDictType;

/**
 * 字典类型 Service。
 */
public interface SysDictTypeService extends BaseService<SysDictType> {

    /**
     * 创建字典类型。
     */
    Long create(SysDictType dictType);

    /**
     * 更新字典类型。
     */
    void update(SysDictType dictType);

    /**
     * 删除字典类型（逻辑删除）。
     */
    void delete(Long id);

    /**
     * 分页查询字典类型。
     */
    PageResult<SysDictType> page(PageParam pageParam, SysDictType query);

    /**
     * 根据编码获取字典类型。
     */
    SysDictType getByCode(String code);
}
