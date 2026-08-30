package com.sw.ck.system.service;

import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseService;
import com.sw.ck.system.entity.SysDictData;

import java.util.List;

/**
 * 字典数据 Service。
 */
public interface SysDictDataService extends BaseService<SysDictData> {

    /**
     * 创建字典数据项。
     */
    Long create(SysDictData dictData);

    /**
     * 更新字典数据项。
     */
    void update(SysDictData dictData);

    /**
     * 删除字典数据项（逻辑删除）。
     */
    void delete(Long id);

    /**
     * 分页查询字典数据项。
     */
    PageResult<SysDictData> page(PageParam pageParam, SysDictData query);

    /**
     * 根据字典类型编码查询字典数据项列表（按 sort 升序，不含停用项）。
     */
    List<SysDictData> listByDictCode(String dictCode);

    /**
     * 校验指定字典类型编码下是否存在指定字典值。
     */
    boolean isValidCode(String dictCode, String value);

    /**
     * 根据字典类型编码和字典值查询标签。
     */
    String resolveLabel(String dictCode, String value);
}
