package com.sw.ck.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 大模型接入配置（M07-F01）。
 * <p>
 * 继承 {@link BaseEntity}（含 id/tenantId/createTime/createBy/updateTime/updateBy/deleted/version）。
 * API Key 仅以密文（AesGcmCipher 加密结果）落库，{@code apiKeyCipher} 由 {@code @JsonIgnore}
 * 屏蔽，禁止直接序列化输出（对外统一走 {@code AgentModelConfigDTO.apiKeyMasked}）。
 * </p>
 * <p>
 * 多Key轮询/额度限流（M07-Step5，V24 追加列）：{@code groupKey} 归组（null=独立配置不参与轮询，
 * 行为与 Step1-4 完全一致）、{@code sort} 组内优先级（数值越小越优先，沿用仓库 {@code sort}
 * 裸名先例）、{@code lockedUntil} 限流临时锁定时间点（惰性过期：null 或已过期即视为可用）、
 * {@code quotaCooldownSeconds} 锁定冷却时长（秒，DB 默认 60）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_agent_model_config")
public class AgentModelConfig extends BaseEntity {

    /** 显示名称，租户内唯一（uk_sw_agent_model_name (tenant_id, name)） */
    private String name;

    /** 协议类型：openai / ollama / other（varchar + String，仓库惯例，不建 enum 类） */
    private String protocolType;

    /** 模型服务地址 */
    private String baseUrl;

    /** 模型标识（如 gpt-4o / llama3） */
    private String modelName;

    /** API Key 密文（AesGcmCipher 加密结果，Base64），Ollama 等无需鉴权场景可为 null */
    @JsonIgnore
    private String apiKeyCipher;

    private BigDecimal temperature;

    private Integer maxTokens;

    private BigDecimal topP;

    /** 请求超时（秒），默认 30 */
    private Integer timeoutSeconds;

    /** 重试次数，默认 0 */
    private Integer retryCount;

    /** 1=启用 0=停用 */
    private Boolean enabled;

    private String remark;

    /** 多Key轮询候选分组标识（V24），null=独立配置不参与轮询 */
    private String groupKey;

    /** 组内优先级（V24），数值越小优先级越高，DB 默认 0 */
    private Integer sort;

    /** 限流临时锁定至该时间点（V24），null 或已过期=可用（惰性过期，无清理任务） */
    private LocalDateTime lockedUntil;

    /** 触发限流后的锁定冷却时长（V24，秒），DB 默认 60 */
    private Integer quotaCooldownSeconds;
}
