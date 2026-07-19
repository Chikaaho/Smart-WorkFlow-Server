package com.sw.ck.storage.service;

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
}
