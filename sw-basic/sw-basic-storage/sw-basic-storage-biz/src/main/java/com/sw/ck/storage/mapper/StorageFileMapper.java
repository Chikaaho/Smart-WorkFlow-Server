package com.sw.ck.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sw.ck.storage.entity.StorageFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件存储记录 Mapper。
 */
@Mapper
public interface StorageFileMapper extends BaseMapper<StorageFile> {
}
