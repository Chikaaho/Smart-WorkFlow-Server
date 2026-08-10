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

    /** 多Key轮询候选分组标识，null=独立配置不参与轮询 */
    private String groupKey;

    /** 组内优先级，数值越小优先级越高 */
    private Integer sort;

    /** 限流临时锁定至该时间点（只读展示，便于运营侧观察哪个 Key 当前被锁定） */
    private LocalDateTime lockedUntil;

    /** 触发限流后的锁定冷却时长（秒） */
    private Integer quotaCooldownSeconds;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
