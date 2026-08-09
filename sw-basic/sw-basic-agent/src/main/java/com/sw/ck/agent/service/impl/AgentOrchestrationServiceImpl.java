package com.sw.ck.agent.service.impl;

import com.sw.ck.agent.dto.AgentOrchestrationRunReqDTO;
import com.sw.ck.agent.dto.AgentOrchestrationRunRespDTO;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.agent.mapper.AgentModelConfigMapper;
import com.sw.ck.agent.orchestration.AgentGraphFactory;
import com.sw.ck.agent.orchestration.AgentToolCallbackFactory;
import com.sw.ck.agent.orchestration.ChatModelFactory;
import com.sw.ck.agent.service.AgentOrchestrationService;
import com.sw.ck.common.crypto.AesGcmCipher;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
                        resp.setSuccess(true);
                        resp.setOutput(String.valueOf(output.get()));
                    }
                }
            } finally {
                // bind/clear 对称：正常完成与异常完成（invoke 抛异常）均执行清除，防 ThreadLocal 泄漏
                AgentGraphFactory.clearChatModel();
                AgentGraphFactory.clearTools();
            }
        } catch (IllegalArgumentException e) {
            // 协议不支持/配置非法：ChatModelFactory 拒绝构造 → success=false（方案 §11 边界）
            resp.setSuccess(false);
            resp.setErrorMessage(summarizeError(e));
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
