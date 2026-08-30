package com.sw.ck.agent.controller;

import com.sw.ck.agent.dto.AgentModelConfigDTO;
import com.sw.ck.agent.dto.AgentModelSaveReqDTO;
import com.sw.ck.agent.dto.AgentModelTestConnectionRespDTO;
import com.sw.ck.agent.service.AgentModelConfigService;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.response.R;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大模型接入配置管理 Controller。
 * <p>
 * 权限码三分（不得合并，方案 §10 约束 7）：{@code agent:model:view}（查询）/
 * {@code agent:model:manage}（增/改/删）/ {@code agent:model:test}（连通性测试）。
 * 写法对齐 {@code ExternalDatasourceController} 的 {@code @ss.hasPermi(...)} 模式。
 * </p>
 */
@RestController
@RequestMapping("/agent/models")
public class AgentModelController {

    private final AgentModelConfigService agentModelConfigService;

    public AgentModelController(AgentModelConfigService agentModelConfigService) {
        this.agentModelConfigService = agentModelConfigService;
    }

    /** 分页查询（apiKey 脱敏） */
    @GetMapping
    @PreAuthorize("@ss.hasPermi('agent:model:view')")
    public R<PageResult<AgentModelConfigDTO>> page(PageParam pageParam,
                                                   @RequestParam(required = false) String nameKeyword) {
        return R.ok(agentModelConfigService.pageModels(pageParam, nameKeyword));
    }

    /** 详情（apiKey 脱敏） */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('agent:model:view')")
    public R<AgentModelConfigDTO> get(@PathVariable Long id) {
        return R.ok(agentModelConfigService.getById(id));
    }

    /** 新增（apiKey 明文入参，Service 层加密落库） */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('agent:model:manage')")
    public R<Long> create(@RequestBody AgentModelSaveReqDTO req) {
        return R.ok(agentModelConfigService.create(req));
    }

    /** 编辑（apiKey 为空时保留旧密文） */
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('agent:model:manage')")
    public R<Void> update(@PathVariable Long id, @RequestBody AgentModelSaveReqDTO req) {
        agentModelConfigService.update(id, req);
        return R.ok();
    }

    /** 删除（逻辑删除） */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('agent:model:manage')")
    public R<Void> delete(@PathVariable Long id) {
        agentModelConfigService.delete(id);
        return R.ok();
    }

    /** 连通性测试（独立权限码：涉及出站网络调用） */
    @PostMapping("/{id}/test-connection")
    @PreAuthorize("@ss.hasPermi('agent:model:test')")
    public R<AgentModelTestConnectionRespDTO> testConnection(@PathVariable Long id) {
        return R.ok(agentModelConfigService.testConnection(id));
    }
}
