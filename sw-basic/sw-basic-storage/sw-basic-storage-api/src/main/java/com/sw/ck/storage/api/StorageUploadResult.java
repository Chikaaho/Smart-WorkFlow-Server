package com.sw.ck.storage.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传结果 DTO。
 * <p>
 * 封装文件上传成功后的元信息，供上层模块消费。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageUploadResult {

    /**
     * 存储唯一标识（提供商侧的文件 key/objectName）。
     */
    private String storageKey;

    /**
     * 存储文件名（系统重命名后的文件名，含扩展名）。
     */
    private String storageName;

    /**
     * 文件访问地址。
     */
    private String storageUrl;

    /**
     * 文件大小（字节）。
     */
    private Long fileSize;
}
