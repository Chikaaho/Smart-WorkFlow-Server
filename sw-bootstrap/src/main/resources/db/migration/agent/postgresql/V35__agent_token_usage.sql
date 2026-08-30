-- ===================================================================
-- Smart-WorkFlow :: V35: Agent Token Usage 统计字段 (PostgreSQL)
-- M07-F04-02 Agent Token 使用统计可观测闭环
-- 为以下表添加 input_tokens / output_tokens 字段：
--   1. sw_agent_message        — 会话消息级 Token 记录
--   2. sw_agent_graph_execution — 图执行级 Token 汇总
--   3. sw_agent_graph_execution_node — 图执行节点级 Token 明细
-- total_tokens 不单独存储，由 input + output 计算得出
-- ===================================================================

-- 1. sw_agent_message: 会话消息级 Token
ALTER TABLE sw_agent_message
    ADD COLUMN input_tokens  BIGINT,
    ADD COLUMN output_tokens BIGINT;

COMMENT ON COLUMN sw_agent_message.input_tokens IS '供应商返回的输入 Token 数（未知时为 NULL，不为 0）';
COMMENT ON COLUMN sw_agent_message.output_tokens IS '供应商返回的输出 Token 数（未知时为 NULL，不为 0）';

-- 2. sw_agent_graph_execution: 图执行级 Token 汇总
ALTER TABLE sw_agent_graph_execution
    ADD COLUMN input_tokens  BIGINT,
    ADD COLUMN output_tokens BIGINT;

COMMENT ON COLUMN sw_agent_graph_execution.input_tokens IS '本次图执行全部 LLM 节点输入 Token 汇总（未知时不参与计算）';
COMMENT ON COLUMN sw_agent_graph_execution.output_tokens IS '本次图执行全部 LLM 节点输出 Token 汇总（未知时不参与计算）';

-- 3. sw_agent_graph_execution_node: 图执行节点级 Token
ALTER TABLE sw_agent_graph_execution_node
    ADD COLUMN input_tokens  BIGINT,
    ADD COLUMN output_tokens BIGINT;

COMMENT ON COLUMN sw_agent_graph_execution_node.input_tokens IS '该节点 LLM 调用的输入 Token（非 LLM 节点或供应商未返回时为 NULL）';
COMMENT ON COLUMN sw_agent_graph_execution_node.output_tokens IS '该节点 LLM 调用的输出 Token（非 LLM 节点或供应商未返回时为 NULL）';
