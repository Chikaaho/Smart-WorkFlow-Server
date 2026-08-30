package com.sw.ck.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件存储配置属性。
 * <p>
 * 支持多向可配置文件存储，通过 {@code sw.storage.active-provider} 切换当前活跃提供商。
 * 配置示例：
 * <pre>{@code
 * sw:
 *   storage:
 *     enabled: true
 *     active-provider: minio
 *     providers:
 *       local:
 *         base-path: ./uploads
 *         url-prefix: /files
 *       minio:
 *         url: http://localhost:9000
 *         access-key: ${MINIO_ACCESS_KEY:}
 *         secret-key: ${MINIO_SECRET_KEY:}
 *         bucket: smart-workflow
 *       cos:
 *         secret-id: ${COS_SECRET_ID:}
 *         secret-key: ${COS_SECRET_KEY:}
 *         region: ap-guangzhou
 *         bucket: ${COS_BUCKET:}
 *       qiniu:
 *         access-key: ${QINIU_ACCESS_KEY:}
 *         secret-key: ${QINIU_SECRET_KEY:}
 *         bucket: ${QINIU_BUCKET:}
 *         domain: ${QINIU_DOMAIN:}
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "sw.storage")
public class StorageProperties {

    /**
     * 是否启用文件存储模块（默认关闭）。
     */
    private boolean enabled = false;

    /**
     * 当前活跃的存储提供商标识（local / minio / cos / qiniu）。
     */
    private String activeProvider = "local";

    /**
     * 各存储提供商配置。
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * 单个存储提供商的配置属性。
     */
    @Data
    public static class ProviderConfig {

        /**
         * 本地存储基路径（仅 local 提供商使用）。
         */
        private String basePath;

        /**
         * URL 前缀（仅 local 提供商使用）。
         */
        private String urlPrefix;

        /**
         * 服务端点地址（仅 minio 使用）。
         */
        private String url;

        /**
         * 访问密钥（minio / qiniu）。
         */
        private String accessKey;

        /**
         * 密钥（minio / cos / qiniu）。
         */
        private String secretKey;

        /**
         * 存储桶名称（minio / cos / qiniu）。
         */
        private String bucket;

        /**
         * 密钥 ID（仅 cos 使用）。
         */
        private String secretId;

        /**
         * 区域（仅 cos 使用）。
         */
        private String region;

        /**
         * 域名（仅 qiniu 使用）。
         */
        private String domain;
    }
}
