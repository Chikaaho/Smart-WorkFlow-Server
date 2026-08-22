-- ===================================================================
-- Smart-WorkFlow :: V35: Agent Token Usage 统计字段 (H2)
-- M07-F04-02 Agent Token 使用统计可观测闭环
-- 为以下表添加 input_tokens / output_tokens 字段：
--   1. sw_agent_message        — 会话消息级 Token 记录
--   2. sw_agent_graph_execution — 图执行级 Token 汇总
--   3. sw_agent_graph_execution_node — 图执行节点级 Token 明细
-- total_tokens 不单独存储，由 input + output 计算得出
-- ===================================================================

-- 1. sw_agent_message: 会话消息级 Token
ALTER TABLE sw_agent_message ADD COLUMN input_tokens BIGINT;
ALTER TABLE sw_agent_message ADD COLUMN output_tokens BIGINT;

-- 2. sw_agent_graph_execution: 图执行级 Token 汇总
ALTER TABLE sw_agent_graph_execution ADD COLUMN input_tokens BIGINT;
ALTER TABLE sw_agent_graph_execution ADD COLUMN output_tokens BIGINT;

-- 3. sw_agent_graph_execution_node: 图执行节点级 Token
ALTER TABLE sw_agent_graph_execution_node ADD COLUMN input_tokens BIGINT;
ALTER TABLE sw_agent_graph_execution_node ADD COLUMN output_tokens BIGINT;
