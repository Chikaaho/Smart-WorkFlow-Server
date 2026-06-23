package com.sw.ck.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 外部数据源执行配置。
 *
 * <pre>
 * sw.external-datasource:
 *   cipher-key: ${SW_CIPHER_KEY}  # AES-256-GCM 密钥，Base64 编码
 *   pool:
 *     max-pool-size: 5
 *     min-idle: 0
 *     idle-timeout: 600000
 *     max-lifetime: 1800000
 *     connection-timeout: 10000
 *   execution:
 *     max-rows: 1000
 *     query-timeout: 30
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "sw.external-datasource")
public class ExternalDatasourceProperties {

    /** AES-256-GCM 加密密钥（Base64 编码，32 字节），从环境变量注入 */
    private String cipherKey;

    /** 连接池配置 */
    private Pool pool = new Pool();

    /** 执行安全配置 */
    private Execution execution = new Execution();

    @Data
    public static class Pool {
        /** 每个外部源连接池最大连接数 */
        private int maxPoolSize = 5;
        /** 最小空闲连接数 */
        private int minIdle = 0;
        /** 空闲连接超时回收（毫秒） */
        private long idleTimeout = 600_000L;
        /** 连接最大生存时间（毫秒） */
        private long maxLifetime = 1_800_000L;
        /** 获取连接超时（毫秒） */
        private long connectionTimeout = 10_000L;
    }

    @Data
    public static class Execution {
        /** 单次查询最大返回行数 */
        private int maxRows = 1000;
        /** 单次查询超时（秒） */
        private int queryTimeout = 30;
    }
}
