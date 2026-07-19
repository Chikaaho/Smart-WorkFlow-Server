package com.sw.ck.storage.service.impl;

import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.storage.entity.StorageFile;
import com.sw.ck.storage.mapper.StorageFileMapper;
import com.sw.ck.storage.service.StorageFileService;
import org.springframework.stereotype.Service;

/**
 * 文件存储 Service 实现。
 */
@Service
public class StorageFileServiceImpl
        extends BaseServiceImpl<StorageFileMapper, StorageFile>
        implements StorageFileService {

    @Override
    public StorageFile findByStorageKey(String storageKey) {
        return lambdaQuery()
                .eq(StorageFile::getStorageKey, storageKey)
                .one();
    }
}
