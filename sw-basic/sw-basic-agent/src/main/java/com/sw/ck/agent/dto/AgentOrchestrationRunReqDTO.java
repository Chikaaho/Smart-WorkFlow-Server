package com.sw.ck.agent.dto;

import lombok.Data;

/**
 * 编排执行请求 DTO（M07 Step2）。
 * <p>
 * 参数非空校验在 Service 层手动完成（模块编译类路径无 jakarta.validation-api，
 * 且 Step1 DTO 亦无 bean-validation 注解先例，沿用仓库惯例）。
 * </p>
 */
@Data
public class AgentOrchestrationRunReqDTO {

    /** 大模型接入配置 id（必填，不存在返回 404 语义） */
    private Long agentModelConfigId;

    /** 用户输入文本（必填，非空） */
    private String input;
}
