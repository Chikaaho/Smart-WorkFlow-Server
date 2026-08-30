package com.sw.ck.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话消息明细（M07 Step4 F04 对话交互）。
 * <p>
 * 每轮编排写入两行：USER（用户输入）+ ASSISTANT（模型最终回复），
 * {@code msgOrder} 为会话内 0-based 顺序号（写入值 = 已有消息数），加载历史时按
 * {@code msgOrder} 升序重建多轮上下文。
 * </p>
 * <p>
 * 角色用字符串（'USER'/'ASSISTANT'/'SYSTEM'）而非 enum，对齐仓库惯例；
 * 大文本 {@code content} 对应 H2=CLOB / PG=TEXT（V22 脚本）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_agent_message")
public class AgentMessage extends BaseEntity {

    /** 所属会话 id（sw_agent_session） */
    private Long sessionId;

    /** 消息角色：USER / ASSISTANT / SYSTEM */
    private String role;

    /** 消息内容（大文本，H2=CLOB / PG=TEXT） */
    private String content;

    /** 会话内顺序号（0-based，单调递增） */
    private Integer msgOrder;

    /** 供应商返回的输入 Token 数（未知时为 NULL，不为 0） */
    private Long inputTokens;

    /** 供应商返回的输出 Token 数（未知时为 NULL，不为 0） */
    private Long outputTokens;
}
