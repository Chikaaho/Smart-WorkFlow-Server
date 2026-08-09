package com.sw.ck.agent.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 大模型接入配置新增/编辑请求 DTO。
 * <p>
 * {@code apiKey} 为明文入参，仅存在于请求边界：非空时由 ServiceImpl 加密落库；
 * 为 null 或空串时 create 场景存 null、update 场景保留旧密文。本 DTO 不参与任何出参序列化。
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
}
