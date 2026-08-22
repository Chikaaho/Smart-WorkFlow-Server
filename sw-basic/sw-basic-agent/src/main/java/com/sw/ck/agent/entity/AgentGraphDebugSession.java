package com.sw.ck.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 图调试会话（M07-F02-04 图单步调试闭环）—— 对应
 * {@code sw_agent_graph_debug_session} 表（V36）。
 * <p>
 * 调试会话在 {@code POST /agent/graph-defs/{id}/debug/start} 时建行，状态
 * {@code PAUSED}（首个断点或起点暂停）/ {@code COMPLETED} / {@code FAILED} /
 * {@code STOPPED}（用户主动停止）/ {@code EXPIRED}（TTL 过期）；执行过程中通过
 * {@code /debug/step} / {@code /debug/resume} 推进，服务端持有可恢复的解释器状态
 * （变量表 / 活动点 / 循环计数 / JOIN 计数 / 下一步信息）序列化于 {@code stateJson}。
 * 大文本列对应 H2=CLOB / PG=TEXT。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_agent_graph_debug_session")
public class AgentGraphDebugSession extends BaseEntity {

    /** 图定义 id（sw_agent_graph_def） */
    private Long graphDefId;

    /** 调试时图定义版本快照（发布锚点） */
    private Integer graphDefVersion;

    /** 调试时图 JSON 快照（大文本，含 elements/edges） */
    private String graphJson;

    /** 调试状态：PAUSED / COMPLETED / FAILED / STOPPED / EXPIRED */
    private String status;

    /** 调试入参文本（大文本） */
    private String input;

    /** 断点列表（JSON 数组，元素为 nodeId） */
    private String breakpoints;

    /**
     * 调试状态快照（JSON，含 variables / activePoints / loopCounts /
     * joinCounts / nextNodeIds 等可恢复上下文）。
     */
    private String stateJson;

    /** 最终输出文本（成功完成时） */
    private String resultText;

    /** 错误分类（失败时，解释器携带） */
    private String errorCategory;

    /** 失败原因摘要（不含明文 API Key） */
    private String errorMessage;

    /** 整次调试执行耗时（毫秒，start 到终态） */
    private Long latencyMs;

    /** 会话过期时间（TTL，到期后置为 EXPIRED） */
    private LocalDateTime expiresAt;

    /** 本次调试全部 LLM 节点输入 Token 汇总 */
    private Long inputTokens;

    /** 本次调试全部 LLM 节点输出 Token 汇总 */
    private Long outputTokens;
}
