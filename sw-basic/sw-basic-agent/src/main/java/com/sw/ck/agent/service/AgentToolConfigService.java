package com.sw.ck.agent.service;

import com.sw.ck.agent.dto.AgentToolExternalConfigDTO;
import com.sw.ck.agent.dto.AgentToolExternalConfigQuery;
import com.sw.ck.agent.dto.AgentToolInternalConfigDTO;
import com.sw.ck.agent.dto.AgentToolInternalConfigQuery;
import com.sw.ck.agent.entity.tool.AgentToolExternalConfig;
import com.sw.ck.agent.entity.tool.AgentToolInternalConfig;
import com.sw.ck.common.page.PageResult;

import java.util.List;

/**
 * 工具沙箱配置管理 Service（内部 + 外部各一组 CRUD + 启用禁用）。
 * <p>
 * 工厂侧查询（{@code listEnabledInternalTools}/{@code listEnabledExternalTools}）供
 * {@code AgentToolCallbackFactory} 加载白名单；{@code tenantId} 为 null 时不显式过滤
 * （租户隔离由 MyBatis-Plus 租户拦截器按登录上下文自动完成）。
 * </p>
 */
public interface AgentToolConfigService {

    // ==================== 内部工具 ====================

    PageResult<AgentToolInternalConfigDTO> pageInternalTools(AgentToolInternalConfigQuery query);

    AgentToolInternalConfigDTO getInternalTool(Long id);

    Long createInternalTool(AgentToolInternalConfigDTO dto);

    void updateInternalTool(Long id, AgentToolInternalConfigDTO dto);

    void deleteInternalTool(Long id);

    void toggleInternalTool(Long id, boolean enabled);

    List<AgentToolInternalConfig> listEnabledInternalTools(Long tenantId);

    // ==================== 外部工具 ====================

    PageResult<AgentToolExternalConfigDTO> pageExternalTools(AgentToolExternalConfigQuery query);

    AgentToolExternalConfigDTO getExternalTool(Long id);

    Long createExternalTool(AgentToolExternalConfigDTO dto);

    void updateExternalTool(Long id, AgentToolExternalConfigDTO dto);

    void deleteExternalTool(Long id);

    void toggleExternalTool(Long id, boolean enabled);

    List<AgentToolExternalConfig> listEnabledExternalTools(Long tenantId);
}
