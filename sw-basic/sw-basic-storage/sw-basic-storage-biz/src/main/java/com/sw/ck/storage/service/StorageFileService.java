package com.sw.ck.storage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sw.ck.common.service.BaseService;
import com.sw.ck.storage.entity.StorageFile;

/**
 * 文件存储 Service 接口。
 */
public interface StorageFileService extends BaseService<StorageFile> {

    /**
     * 按存储 key 查询文件记录。
     * <p>
     * 租户条件由 {@code TenantLineHandler} 自动注入，不手写 tenant 条件。
     * </p>
     *
     * @param storageKey 存储唯一标识
     * @return 文件记录，不存在时返回 null
     */
    StorageFile findByStorageKey(String storageKey);

    /**
     * 文件列表分页（create_time 倒序，数据范围纳管入口）。
     * <p>
     * 数据范围等效条件（sw_storage_file 无 dept_id 列）在
     * {@code StorageFileMapper#selectStorageFilePage} 内实现。
     * </p>
     *
     * @param pageNum  页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页结果（Page 对象由 MP 分页拦截器填充）
     */
    Page<StorageFile> pageFiles(long pageNum, long pageSize);
}
