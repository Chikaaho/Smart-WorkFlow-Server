package com.sw.ck.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sw.ck.agent.dto.AgentModelConfigDTO;
import com.sw.ck.agent.dto.AgentModelSaveReqDTO;
import com.sw.ck.agent.dto.AgentModelTestConnectionRespDTO;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.agent.mapper.AgentModelConfigMapper;
import com.sw.ck.agent.service.AgentModelConfigService;
import com.sw.ck.common.crypto.AesGcmCipher;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseServiceImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Set;

/**
 * 大模型接入配置 Service 实现。
 * <p>
 * <b>加密调用点唯一化（方案 §10 约束 1）</b>：{@code AesGcmCipher.encrypt/decrypt} 与
 * {@code AesGcmCipher.mask}（静态）只在本类内调用。解密出的明文 Key 仅存在于局部变量，
 * 用于当次脱敏或当次连通性测试请求头，不赋值给任何字段、不打日志、不进异常消息，
 * 方法返回前显式置 null 释放引用（方案 §10 约束 2）。
 * </p>
 * <p>
 * <b>本 Step 明确不做</b>（推迟后续 Step）：动态装载（运行期切换生效）、多 Key 轮询、
 * 额度限流；{@code retryCount}/{@code timeoutSeconds} 仅落库存储，不在连通性测试中生效。
 * </p>
 */
@Service
public class AgentModelConfigServiceImpl
        extends BaseServiceImpl<AgentModelConfigMapper, AgentModelConfig>
        implements AgentModelConfigService {

    /** 协议类型白名单（varchar + String，不建 enum 类，仓库惯例） */
    private static final Set<String> PROTOCOL_TYPES = Set.of("openai", "ollama", "other");

    /** 连通性测试连接/读取超时 */
    private static final Duration TEST_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration TEST_READ_TIMEOUT = Duration.ofSeconds(5);

    private final AesGcmCipher cipher;

    public AgentModelConfigServiceImpl(AesGcmCipher cipher) {
        this.cipher = cipher;
    }

    // ==================== 查询 ====================

    @Override
    public PageResult<AgentModelConfigDTO> pageModels(PageParam pageParam, String nameKeyword) {
        LambdaQueryWrapper<AgentModelConfig> wrapper = Wrappers.lambdaQuery();
        if (nameKeyword != null && !nameKeyword.isBlank()) {
            wrapper.like(AgentModelConfig::getName, nameKeyword);
        }
        wrapper.orderByDesc(AgentModelConfig::getId);
        Page<AgentModelConfig> page = page(new Page<>(pageParam.getPageNum(), pageParam.getPageSize()), wrapper);
        return PageResult.of(page.convert(this::toDTO));
    }

    @Override
    public AgentModelConfigDTO getById(Long id) {
        AgentModelConfig entity = requireEntity(id);
        AgentModelConfigDTO dto = toDTO(entity);
        if (entity.getApiKeyCipher() != null && !entity.getApiKeyCipher().isEmpty()) {
            // 解密 → 立即脱敏 → 明文局部变量生命周期到此结束（置 null 释放引用）
            String plain = cipher.decrypt(entity.getApiKeyCipher());
            dto.setApiKeyMasked(AesGcmCipher.mask(plain));
            plain = null;
        }
        return dto;
    }

    // ==================== 写操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AgentModelSaveReqDTO req) {
        validateProtocol(req.getProtocolType());
        checkNameUnique(req.getName(), null);
        AgentModelConfig entity = toEntity(req);
        // apiKey 非空 → 加密落库；为 null/空串 → 密文存 null（如纯 Ollama 本地部署无需鉴权）
        if (req.getApiKey() != null && !req.getApiKey().isEmpty()) {
            entity.setApiKeyCipher(cipher.encrypt(req.getApiKey()));
        }
        try {
            save(entity);
        } catch (DuplicateKeyException e) {
            // 并发竞态兜底：唯一索引 uk_sw_agent_model_name (tenant_id, name) 冲突 → 转业务异常
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "模型名称已存在：" + req.getName());
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AgentModelSaveReqDTO req) {
        AgentModelConfig existing = requireEntity(id);
        validateProtocol(req.getProtocolType());
        checkNameUnique(req.getName(), id);
        AgentModelConfig entity = toEntity(req);
        entity.setId(id);
        if (req.getApiKey() != null && !req.getApiKey().isEmpty()) {
            entity.setApiKeyCipher(cipher.encrypt(req.getApiKey()));
        } else {
            // apiKey 为空 → 不覆盖旧密文（方案 §10 约束 4：唯一允许的"部分更新"字段）
            entity.setApiKeyCipher(existing.getApiKeyCipher());
        }
        try {
            updateById(entity);
        } catch (DuplicateKeyException e) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "模型名称已存在：" + req.getName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 逻辑删除（@TableLogic），幂等：不存在也直接返回成功
        removeById(id);
    }

    // ==================== 连通性测试 ====================

    @Override
    public AgentModelTestConnectionRespDTO testConnection(Long id) {
        AgentModelConfig entity = requireEntity(id);
        // 解密明文 Key：仅用于本次请求头，用完立即置 null 释放引用
        String apiKey = null;
        if (entity.getApiKeyCipher() != null && !entity.getApiKeyCipher().isEmpty()) {
            apiKey = cipher.decrypt(entity.getApiKeyCipher());
        }
        long start = System.currentTimeMillis();
        AgentModelTestConnectionRespDTO resp = new AgentModelTestConnectionRespDTO();
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout((int) TEST_CONNECT_TIMEOUT.toMillis());
            requestFactory.setReadTimeout((int) TEST_READ_TIMEOUT.toMillis());
            RestClient client = RestClient.builder()
                    .baseUrl(entity.getBaseUrl())
                    .requestFactory(requestFactory)
                    .build();
            String path = switch (entity.getProtocolType()) {
                case "openai" -> "/models";
                case "ollama" -> "/api/tags";
                default -> ""; // "other"（或未知值兜底）：仅探测可达性，不校验响应体
            };
            RestClient.RequestHeadersSpec<?> spec = client.get().uri(path);
            if (apiKey != null && !apiKey.isEmpty() && "openai".equals(entity.getProtocolType())) {
                spec = spec.header("Authorization", "Bearer " + apiKey);
            }
            spec.retrieve().toBodilessEntity();
            resp.setSuccess(true);
            resp.setMessage("连接成功");
        } catch (RestClientResponseException e) {
            // 4xx/5xx：服务端可达（鉴权/路径问题），判定成功
            resp.setSuccess(true);
            resp.setMessage("服务可达（HTTP " + e.getStatusCode().value() + "）");
        } catch (ResourceAccessException e) {
            // 连接超时/拒绝/DNS 失败等网络异常：判定失败，message 只含网络层信息（不含 Key）
            resp.setSuccess(false);
            String detail = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            resp.setMessage(detail != null ? detail : "网络不可达");
        }
        resp.setLatencyMs(System.currentTimeMillis() - start);
        apiKey = null;
        return resp;
    }

    // ==================== 内部辅助 ====================

    private void validateProtocol(String protocolType) {
        if (protocolType == null || !PROTOCOL_TYPES.contains(protocolType)) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR,
                    "不支持的协议类型：" + protocolType + "（仅支持 openai/ollama/other）");
        }
    }

    private void checkNameUnique(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "模型名称不能为空");
        }
        boolean exists = lambdaQuery()
                .eq(AgentModelConfig::getName, name)
                .ne(excludeId != null, AgentModelConfig::getId, excludeId)
                .exists();
        if (exists) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "模型名称已存在：" + name);
        }
    }

    /**
     * 按 id + 租户加载（baseMapper.selectById 经租户拦截器自动过滤 tenant_id），
     * 不存在抛 NOT_FOUND。注意：不能调用 this.getById(id)（会解析到本类 DTO 重载，死循环）。
     */
    private AgentModelConfig requireEntity(Long id) {
        if (id == null) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "id 不能为空");
        }
        AgentModelConfig entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(CommonErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private AgentModelConfig toEntity(AgentModelSaveReqDTO req) {
        AgentModelConfig entity = new AgentModelConfig();
        entity.setName(req.getName());
        entity.setProtocolType(req.getProtocolType());
        entity.setBaseUrl(req.getBaseUrl());
        entity.setModelName(req.getModelName());
        entity.setTemperature(req.getTemperature());
        entity.setMaxTokens(req.getMaxTokens());
        entity.setTopP(req.getTopP());
        entity.setTimeoutSeconds(req.getTimeoutSeconds());
        entity.setRetryCount(req.getRetryCount());
        entity.setEnabled(req.getEnabled());
        entity.setRemark(req.getRemark());
        return entity;
    }

    private AgentModelConfigDTO toDTO(AgentModelConfig entity) {
        AgentModelConfigDTO dto = new AgentModelConfigDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setProtocolType(entity.getProtocolType());
        dto.setBaseUrl(entity.getBaseUrl());
        dto.setModelName(entity.getModelName());
        dto.setTemperature(entity.getTemperature());
        dto.setMaxTokens(entity.getMaxTokens());
        dto.setTopP(entity.getTopP());
        dto.setTimeoutSeconds(entity.getTimeoutSeconds());
        dto.setRetryCount(entity.getRetryCount());
        dto.setEnabled(entity.getEnabled());
        dto.setRemark(entity.getRemark());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
