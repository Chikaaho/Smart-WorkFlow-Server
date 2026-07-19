package com.sw.ck.storage.provider;

import com.sw.ck.storage.api.StorageUploadResult;

import java.io.InputStream;

/**
 * 文件存储提供商抽象接口。
 * <p>
 * 定义统一的文件上传、下载、删除、URL 获取和类型标识能力。
 * 所有存储提供商（local / minio / cos / qiniu）必须实现此接口。
 * </p>
 */
public interface StorageProvider {

    /**
     * 上传文件。
     *
     * @param inputStream 文件输入流
     * @param storageName 存储文件名（含扩展名）
     * @param contentType 文件 MIME 类型
     * @return 上传结果
     */
    StorageUploadResult upload(InputStream inputStream, String storageName, String contentType);

    /**
     * 下载文件。
     *
     * @param storageKey 存储唯一标识
     * @return 文件输入流
     */
    InputStream download(String storageKey);

    /**
     * 删除文件。
     *
     * @param storageKey 存储唯一标识
     */
    void delete(String storageKey);

    /**
     * 获取文件访问 URL。
     *
     * @param storageKey 存储唯一标识
     * @return 文件可访问 URL
     */
    String getUrl(String storageKey);

    /**
     * 获取存储提供商类型标识。
     *
     * @return 提供商类型（local / minio / cos / qiniu）
     */
    String getType();
}
