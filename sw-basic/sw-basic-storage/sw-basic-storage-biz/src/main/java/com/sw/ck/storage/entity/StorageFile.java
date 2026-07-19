package com.sw.ck.storage.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件存储记录实体。
 * <p>
 * 记录文件存储元信息，支持多提供商（本地/MinIO/COS/七牛云）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_storage_file")
public class StorageFile extends BaseEntity {

    /**
     * 文件原始名称（上传时的文件名）。
     */
    private String originalName;

    /**
     * 存储唯一标识（提供商侧的文件 key/objectName）。
     */
    private String storageKey;

    /**
     * 存储文件名（系统重命名后的文件名，含扩展名）。
     */
    private String storageName;

    /**
     * 文件大小（字节）。
     */
    private Long fileSize;

    /**
     * 文件 MIME 类型。
     */
    private String contentType;

    /**
     * 文件扩展名（小写，不含点，如 "pdf"）。
     */
    private String fileExt;

    /**
     * 存储提供商类型（local / minio / cos / qiniu）。
     */
    private String providerType;

    /**
     * 存储桶名称（本地模式为目录名）。
     */
    private String bucketName;

    /**
     * 文件访问地址。
     */
    private String storageUrl;
}
