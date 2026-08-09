package com.sw.ck.agent.service;

import com.sw.ck.agent.dto.AgentModelConfigDTO;
import com.sw.ck.agent.dto.AgentModelSaveReqDTO;
import com.sw.ck.agent.dto.AgentModelTestConnectionRespDTO;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;

/**
 * 大模型接入配置 Service（CRUD + 分页 + 连通性测试）。
 * <p>
 * 加密/解密/脱敏调用点唯一化：仅本接口实现类（{@code AgentModelConfigServiceImpl}）内
 * 允许调用 {@code AesGcmCipher} 的 encrypt/decrypt/mask，Controller/DTO 层不出现明文 Key。
 * </p>
 */
public interface AgentModelConfigService {

    PageResult<AgentModelConfigDTO> pageModels(PageParam pageParam, String nameKeyword);

    AgentModelConfigDTO getById(Long id);

    Long create(AgentModelSaveReqDTO req);

    void update(Long id, AgentModelSaveReqDTO req);

    void delete(Long id);

    AgentModelTestConnectionRespDTO testConnection(Long id);
}
