package com.sw.ck.agent.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 大模型接入配置新增/编辑请求 DTO。
 * <p>
 * {@code apiKey} 为明文入参，仅存在于请求边界：非空时由 ServiceImpl 加密落库；
 * 为 null 或空串时 create 场景存 null、update 场景保留旧密文。本 DTO 不参与任何出参序列化。
 * </p>
 * <p>
 * 多Key轮询（M07-Step5）：{@code groupKey}/{@code sort}/{@code quotaCooldownSeconds} 用户可配置；
 * {@code lockedUntil} 为系统运行态（限流触发时由编排引擎写入），不对外暴露为可写字段。
 * </p>
 */
@Data
public class AgentModelSaveReqDTO {

    /** 显示名称，租户内唯一 */
    private String name;

    /** 协议类型：openai / ollama / other（Service 层白名单校验） */
    private String protocolType;

    /** 模型服务地址 */
    private String baseUrl;

    /** 模型标识（如 gpt-4o / llama3） */
    private String modelName;

    /** API Key 明文，可为空（Ollama 本地部署无需鉴权） */
    private String apiKey;

    private BigDecimal temperature;

    private Integer maxTokens;

    private BigDecimal topP;

    private Integer timeoutSeconds;

    private Integer retryCount;

    private Boolean enabled;

    private String remark;

    /** 多Key轮询候选分组标识（M07-Step5），null=独立配置不参与轮询 */
    private String groupKey;

    /** 组内优先级，数值越小优先级越高（DB 默认 0） */
    private Integer sort;

    /** 触发限流后的锁定冷却时长（秒，DB 默认 60） */
    private Integer quotaCooldownSeconds;
}
