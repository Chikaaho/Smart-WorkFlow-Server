package com.sw.ck.agent.controller;

import com.sw.ck.agent.dto.AgentGraphCreateReqDTO;
import com.sw.ck.agent.dto.AgentGraphDefDTO;
import com.sw.ck.agent.dto.graph.ProcessGraph;
import com.sw.ck.agent.service.AgentGraphDefService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 图定义管理 Controller（M07-F02 Step7 图定义 CRUD + 版本 + 发布骨架）。
 * <p>
 * 纯存储+管理端点，无执行语义。权限沿用既有两枚（不新增权限码，方案 §4-C）：
 * {@code agent:model:view}（查询）/ {@code agent:model:manage}（增/改/删/发布）。
 * 写法对齐 {@code AgentModelController} 的 {@code @ss.hasPermi(...)} 模式。
 * </p>
 */
@RestController
@RequestMapping("/agent/graph-defs")
public class AgentGraphDefController {

    private final AgentGraphDefService agentGraphDefService;

    public AgentGraphDefController(AgentGraphDefService agentGraphDefService) {
        this.agentGraphDefService = agentGraphDefService;
    }

    /** 创建图定义（服务端生成 graphKey + 初始图 START→END，DRAFT） */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('agent:model:manage')")
    public R<Long> create(@RequestBody AgentGraphCreateReqDTO req) {
        return R.ok(agentGraphDefService.create(req.getName()));
    }

    /** 保存草稿图（全量覆盖 graph_json，status 保持 DRAFT，不跑校验） */
    @PutMapping("/{id}/graph")
    @PreAuthorize("@ss.hasPermi('agent:model:manage')")
    public R<Void> saveDraftGraph(@PathVariable Long id, @RequestBody ProcessGraph graph) {
        agentGraphDefService.saveDraftGraph(id, graph);
        return R.ok();
    }

    /** 发布图定义（defVersion 递增 + PUBLISHED + graphKey 冻结检查） */
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermi('agent:model:manage')")
    public R<AgentGraphDefDTO> publish(@PathVariable Long id) {
        return R.ok(agentGraphDefService.publish(id));
    }

    /** 详情（设计器回显：返回解析后的 ProcessGraph，含 elements） */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('agent:model:view')")
    public R<ProcessGraph> getGraph(@PathVariable Long id) {
        return R.ok(agentGraphDefService.getGraph(id));
    }

    /** 分页列表（DTO 不含 graph_json 大字段） */
    @GetMapping
    @PreAuthorize("@ss.hasPermi('agent:model:view')")
    public R<PageResult<AgentGraphDefDTO>> pageDefs(PageParam pageParam) {
        return R.ok(agentGraphDefService.pageDefs(pageParam));
    }

    /** 删除（逻辑删除，幂等） */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('agent:model:manage')")
    public R<Void> delete(@PathVariable Long id) {
        agentGraphDefService.delete(id);
        return R.ok();
    }
}
