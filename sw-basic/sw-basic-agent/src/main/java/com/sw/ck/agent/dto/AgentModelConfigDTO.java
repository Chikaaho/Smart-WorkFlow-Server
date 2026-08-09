package com.sw.ck.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 大模型接入配置响应 DTO。
 * <p>
 * 安全约束：本 DTO 只含 {@code apiKeyMasked}（前 2 后 2 + **** 脱敏串），
 * 不含 {@code apiKeyCipher}（密文）也不含明文 Key——明文生命周期仅在 ServiceImpl 内部。
 * </p>
 */
@Data
public class AgentModelConfigDTO {

    private Long id;

    private String name;

    private String protocolType;

    private String baseUrl;

    private String modelName;

    /** API Key 脱敏展示（AesGcmCipher.mask(明文) 的结果），未配置时为 null */
    private String apiKeyMasked;

    private BigDecimal temperature;

    private Integer maxTokens;

    private BigDecimal topP;

    private Integer timeoutSeconds;

    private Integer retryCount;

    private Boolean enabled;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
