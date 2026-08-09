package com.sw.ck.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sw.ck.agent.dto.AgentOrchestrationRunReqDTO;
import com.sw.ck.agent.dto.AgentOrchestrationRunRespDTO;
import com.sw.ck.agent.entity.AgentMessage;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.agent.entity.AgentSession;
import com.sw.ck.agent.entity.AgentToolCallLog;
import com.sw.ck.agent.mapper.AgentMessageMapper;
import com.sw.ck.agent.mapper.AgentModelConfigMapper;
import com.sw.ck.agent.mapper.AgentSessionMapper;
import com.sw.ck.agent.mapper.AgentToolCallLogMapper;
import com.sw.ck.agent.orchestration.AgentGraphFactory;
import com.sw.ck.agent.orchestration.AgentToolCallbackFactory;
import com.sw.ck.agent.orchestration.ChatModelFactory;
import com.sw.ck.agent.orchestration.ToolCallRecord;
import com.sw.ck.agent.service.AgentOrchestrationService;
import com.sw.ck.common.crypto.AesGcmCipher;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 编排执行引擎 Service 实现（M07 Step2）。
 * <p>
 * <b>明文 API Key 生命周期最短化（方案 §10 约束 2）</b>：解密出的明文 Key 仅存在于
 * 局部变量，用于当次 {@code ChatModelFactory.build}，不进日志、不进异常消息、不进
 * 响应 DTO 任何字段，方法结束前置 null 释放引用（延续 Step1 同款惯例）。
 * </p>
 * <p>
 * <b>异常处理双保险（依据 langgraph4j 1.5.14 实测）</b>：节点动作抛异常时
 * {@code CompiledGraph.invoke()} 以 {@code CompletionException}（CompletableFuture.join
 * 包装）原样抛出、不会返回空 Optional；为稳妥同时兜底"invoke 返回空 Optional /
 * 状态缺 output"两种情况，均转 {@code success=false}。模型服务不可达/超时等经
 * Spring AI RetryTemplate 重试耗尽后抛出，同样转 {@code success=false}，不抛 500。
 * </p>
 */
@Service
public class AgentOrchestrationServiceImpl implements AgentOrchestrationService {

    private final AgentModelConfigMapper mapper;
    private final AesGcmCipher cipher;
    private final ChatModelFactory chatModelFactory;
    private final CompiledGraph<AgentState> agentCompiledGraph;

    /**
     * M07 Step3 工具沙箱工厂（可选注入）。{@code required = false}：保留 Step2 的
     * 4 参直构路径（既有测试直接 new，不注入工厂时行为与 Step2 完全一致——不加载
     * 工具、不绑定、不清理）；生产环境由 Spring 注入（{@code sw.agent.enabled=true}
     * 时 {@code AgentToolCallbackFactory} 为 Bean）。
     */
    @Autowired(required = false)
    private AgentToolCallbackFactory agentToolCallbackFactory;

    /** M07 Step4 F04 会话主表 Mapper（字段注入：保留 4 参直构路径，既有测试不受影响） */
    @Autowired
    private AgentSessionMapper sessionMapper;

    /** M07 Step4 F04 会话消息明细 Mapper */
    @Autowired
    private AgentMessageMapper messageMapper;

    /** M07 Step4 F04 工具调用日志 Mapper */
    @Autowired
    private AgentToolCallLogMapper toolCallLogMapper;

    /** 会话状态常量：ACTIVE（方案 §3：状态写死，会话永久有效） */
    private static final String SESSION_STATUS_ACTIVE = "ACTIVE";

    /** 消息角色：USER（用户输入） */
    private static final String ROLE_USER = "USER";

    /** 消息角色：ASSISTANT（模型最终回复） */
    private static final String ROLE_ASSISTANT = "ASSISTANT";

    public AgentOrchestrationServiceImpl(AgentModelConfigMapper mapper,
                                         AesGcmCipher cipher,
                                         ChatModelFactory chatModelFactory,
                                         CompiledGraph<AgentState> agentCompiledGraph) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.chatModelFactory = chatModelFactory;
        this.agentCompiledGraph = agentCompiledGraph;
    }

    @Override
    public AgentOrchestrationRunRespDTO run(AgentOrchestrationRunReqDTO req) {
        // 参数校验（DTO 层无 bean-validation：模块类路径无 jakarta.validation-api，
        // 沿用 Step1 Service 层手动校验惯例）
        if (req == null || req.getAgentModelConfigId() == null) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "agentModelConfigId 不能为空");
        }
        if (req.getInput() == null || req.getInput().isBlank()) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "input 不能为空");
        }
        // baseMapper.selectById 经租户拦截器自动过滤 tenant_id（Step1 同款）；不存在 → 404 语义
        AgentModelConfig entity = mapper.selectById(req.getAgentModelConfigId());
        if (entity == null) {
            throw new BaseException(CommonErrorCode.NOT_FOUND);
        }

        // 解密明文 Key：仅用于本次构造 ChatModel，用完置 null 释放引用
        String plainApiKey = null;
        if (entity.getApiKeyCipher() != null && !entity.getApiKeyCipher().isEmpty()) {
            plainApiKey = cipher.decrypt(entity.getApiKeyCipher());
        }

        long start = System.currentTimeMillis();
        AgentOrchestrationRunRespDTO resp = new AgentOrchestrationRunRespDTO();
        try {
            ChatModel chatModel = chatModelFactory.build(entity, plainApiKey);
            // M07 Step4 F04：会话获取/创建 + 历史消息加载（在 chatModel 构造之后：
            // 配置非法时 ChatModelFactory 抛 IllegalArgumentException，不落会话脏数据）
            Long sessionId = req.getSessionId();
            List<AgentMessage> dbMessages;
            if (sessionId == null) {
                AgentSession session = new AgentSession();
                session.setAgentModelConfigId(req.getAgentModelConfigId());
                session.setStatus(SESSION_STATUS_ACTIVE);
                // id（雪花 ASSIGN_ID）/createTime/createBy/tenantId/deleted/version
                // 由 MyBatis-Plus + CommonMetaObjectHandler 填充
                sessionMapper.insert(session);
                sessionId = session.getId();
                dbMessages = List.of();
            } else {
                // selectById 经租户拦截器自动过滤 tenant_id：跨租户/已删除/不存在会话 → null → 404 语义
                AgentSession existing = sessionMapper.selectById(sessionId);
                if (existing == null) {
                    throw new BaseException(CommonErrorCode.NOT_FOUND, "会话不存在");
                }
                dbMessages = loadHistoryMessages(sessionId);
            }
            // 历史消息经 ThreadLocal 注入 callModel 节点（与 chatModel/tools 同款绑定模式）
            AgentGraphFactory.bindHistoryMessages(toSpringAiMessages(dbMessages));
            AgentGraphFactory.bindToolCallRecords(new ArrayList<>());
            // M07 Step3：加载本租户启用的工具白名单 → 绑定到图执行线程（无工具/工厂
            // 未注入时跳过，Prompt 构造与 Step2 完全一致）；租户隔离由租户拦截器自动完成
            // （buildToolCallbacks(null) 不显式过滤，同 Step2 selectById 先例）
            List<ToolCallback> tools = agentToolCallbackFactory == null
                    ? List.of()
                    : agentToolCallbackFactory.buildToolCallbacks(null);
            AgentGraphFactory.bindChatModel(chatModel);
            if (!tools.isEmpty()) {
                AgentGraphFactory.bindTools(tools);
            }
            try {
                Optional<AgentState> result = agentCompiledGraph.invoke(
                        Map.of("input", req.getInput(), "chatModel", chatModel));
                if (result.isEmpty()) {
                    // 兜底：invoke 返回空 Optional（实测正常路径不会发生）
                    resp.setSuccess(false);
                    resp.setErrorMessage("编排引擎执行未产生结果");
                } else {
                    Optional<Object> output = result.get().value("output");
                    if (output.isEmpty()) {
                        resp.setSuccess(false);
                        resp.setErrorMessage("编排引擎执行未产生输出");
                    } else {
                        String outputText = String.valueOf(output.get());
                        resp.setSuccess(true);
                        resp.setOutput(outputText);
                        // M07 Step4 F04：持久化本轮 USER + ASSISTANT 消息（msg_order =
                        // 已有消息数，0-based 单调递增）与工具调用日志，并回传会话 id
                        int nextOrder = dbMessages.size();
                        insertMessage(sessionId, ROLE_USER, req.getInput(), nextOrder);
                        insertMessage(sessionId, ROLE_ASSISTANT, outputText, nextOrder + 1);
                        persistToolCallLogs(sessionId);
                        resp.setSessionId(sessionId);
                    }
                }
            } finally {
                // bind/clear 对称：正常完成与异常完成（invoke 抛异常）均执行清除，防 ThreadLocal 泄漏
                AgentGraphFactory.clearChatModel();
                AgentGraphFactory.clearTools();
                AgentGraphFactory.clearHistoryMessages();
                AgentGraphFactory.clearToolCallRecords();
            }
        } catch (IllegalArgumentException e) {
            // 协议不支持/配置非法：ChatModelFactory 拒绝构造 → success=false（方案 §11 边界）
            resp.setSuccess(false);
            resp.setErrorMessage(summarizeError(e));
        } catch (BaseException e) {
            // 业务异常（如会话不存在）保持上抛，由全局异常处理器转 404 语义，不吞为 success=false
            throw e;
        } catch (Exception e) {
            // 模型服务不可达/超时/节点异常等：转 success=false + 异常摘要
            resp.setSuccess(false);
            resp.setErrorMessage(summarizeError(e));
        } finally {
            plainApiKey = null;
        }
        resp.setLatencyMs(System.currentTimeMillis() - start);
        return resp;
    }

    /**
     * 加载会话历史消息（按 msg_order 升序；租户隔离由租户拦截器自动完成，
     * 与模块全部查询同路径）。
     */
    private List<AgentMessage> loadHistoryMessages(Long sessionId) {
        return messageMapper.selectList(
                Wrappers.<AgentMessage>lambdaQuery()
                        .eq(AgentMessage::getSessionId, sessionId)
                        .orderByAsc(AgentMessage::getMsgOrder));
    }

    /**
     * DB 消息 → Spring AI {@link Message} 列表（USER → {@link UserMessage}，
     * ASSISTANT → {@link AssistantMessage}；其他角色留后续迭代，跳过）。
     * 入参已按 msg_order 升序，转换后顺序保持。
     */
    private List<Message> toSpringAiMessages(List<AgentMessage> dbMessages) {
        List<Message> messages = new ArrayList<>(dbMessages.size());
        for (AgentMessage db : dbMessages) {
            if (ROLE_USER.equals(db.getRole())) {
                messages.add(new UserMessage(db.getContent()));
            } else if (ROLE_ASSISTANT.equals(db.getRole())) {
                messages.add(new AssistantMessage(db.getContent()));
            }
        }
        return messages;
    }

    /** 写入一条会话消息（msg_order 由调用方计算，0-based 单调递增） */
    private void insertMessage(Long sessionId, String role, String content, int msgOrder) {
        AgentMessage msg = new AgentMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setMsgOrder(msgOrder);
        messageMapper.insert(msg);
    }

    /** 将本轮捕获的工具调用记录批量落库（无记录时为空操作） */
    private void persistToolCallLogs(Long sessionId) {
        List<ToolCallRecord> records = AgentGraphFactory.getToolCallRecords();
        if (records == null || records.isEmpty()) {
            return;
        }
        for (ToolCallRecord record : records) {
            AgentToolCallLog log = new AgentToolCallLog();
            log.setSessionId(sessionId);
            log.setToolName(record.getToolName());
            log.setToolCallArgs(record.getArgs());
            log.setToolCallResult(record.getResult());
            log.setLatencyMs(record.getLatencyMs());
            toolCallLogMapper.insert(log);
        }
    }

    /**
     * 异常摘要：沿 cause 链取最深层非空 message（如 CompletionException →
     * ExecutionException → IllegalStateException("...") 取节点真实原因；连接拒绝取
     * ResourceAccessException 的 I/O 描述）。只取 message，不含堆栈，杜绝明文 Key
     * 通过异常信息泄漏（方案 §12 风险表）。
     */
    private String summarizeError(Throwable t) {
        Throwable cur = t;
        String best = null;
        while (cur != null) {
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                best = cur.getMessage();
            }
            cur = cur.getCause();
        }
        return best != null ? best : t.getClass().getSimpleName();
    }
}
