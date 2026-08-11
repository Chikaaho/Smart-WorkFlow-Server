package com.sw.ck.agent.dto;

import lombok.Data;

/**
 * Agent 图定义创建请求 DTO。
 * <p>
 * 仅承载 {@code name}：graphKey 由服务端生成（{@code agent_} 前缀 + UUID 短串），
 * 初始图（START→END）由服务端构造，设计器通过草稿保存端点迭代图内容。
 * </p>
 */
@Data
public class AgentGraphCreateReqDTO {

    /** 图名称（必填，Service 层校验非空） */
    private String name;
}
