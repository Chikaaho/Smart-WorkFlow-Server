package com.sw.ck.bpm.process.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sw.ck.bpm.api.dto.BpmDeployResult;
import com.sw.ck.bpm.api.dto.GraphElement;
import com.sw.ck.bpm.api.dto.GraphValidationError;
import com.sw.ck.bpm.api.dto.ProcessGraph;
import com.sw.ck.bpm.api.exception.BpmErrorCode;
import com.sw.ck.bpm.api.facade.BpmDeployFacade;
import com.sw.ck.bpm.process.entity.BpmProcessDef;
import com.sw.ck.bpm.process.mapper.BpmProcessDefMapper;
import com.sw.ck.bpm.process.service.BpmProcessDefService;
import com.sw.ck.bpm.process.validator.GraphValidator;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.form.api.dto.FormDefDTO;
import com.sw.ck.form.api.form.FormDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 流程定义服务实现。
 */
@Service
public class BpmProcessDefServiceImpl implements BpmProcessDefService {

    private static final Logger log = LoggerFactory.getLogger(BpmProcessDefServiceImpl.class);

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String FORM_STATUS_PUBLISHED = "PUBLISHED";

    private final BpmProcessDefMapper mapper;
    private final GraphValidator graphValidator;
    private final FormDefinitionService formDefinitionService;
    private final BpmDeployFacade bpmDeployFacade;
    private final ObjectMapper objectMapper;

    public BpmProcessDefServiceImpl(BpmProcessDefMapper mapper,
                                    GraphValidator graphValidator,
                                    FormDefinitionService formDefinitionService,
                                    BpmDeployFacade bpmDeployFacade,
                                    ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.graphValidator = graphValidator;
        this.formDefinitionService = formDefinitionService;
        this.bpmDeployFacade = bpmDeployFacade;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public BpmProcessDef createDef(String name, String formKey) {
        // 校验表单存在（2009）
        if (!formDefinitionService.formExists(formKey)) {
            throw new BaseException(BpmErrorCode.GRAPH_FORM_NOT_FOUND);
        }

        // 生成 processKey
        String processKey = "bpm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // 生成初始图：START → END
        ProcessGraph initialGraph = buildInitialGraph(processKey, name, formKey);

        BpmProcessDef entity = new BpmProcessDef();
        entity.setProcessKey(processKey);
        entity.setName(name);
        entity.setFormKey(formKey);
        entity.setDefVersion(1);
        entity.setStatus(STATUS_DRAFT);
        entity.setGraphJson(toJson(initialGraph));

        mapper.insert(entity);
        log.info("Created process def: id={}, processKey={}", entity.getId(), processKey);
        return entity;
    }

    @Override
    @Transactional
    public void saveDraftGraph(Long id, String graphJson) {
        BpmProcessDef entity = getExisting(id);
        entity.setGraphJson(graphJson);
        // status 保持 DRAFT，不跑校验
        mapper.updateById(entity);
        log.info("Saved draft graph: id={}", id);
    }

    @Override
    public List<GraphValidationError> validateGraph(Long id) {
        BpmProcessDef entity = getExisting(id);
        ProcessGraph graph = parseGraph(entity.getGraphJson());
        if (graph == null || graph.getElements() == null) {
            return List.of(GraphValidationError.builder()
                    .errorCode(BpmErrorCode.GRAPH_MISSING_START.getCode())
                    .message("图数据为空")
                    .build());
        }
        return graphValidator.validate(graph.getElements(), entity.getFormKey());
    }

    @Override
    public List<GraphValidationError> validateGraph(ProcessGraph graph) {
        if (graph == null || graph.getElements() == null) {
            return List.of(GraphValidationError.builder()
                    .errorCode(BpmErrorCode.GRAPH_MISSING_START.getCode())
                    .message("图数据为空")
                    .build());
        }
        String formKey = graph.getFormKey();
        // 如果请求中没有 formKey，尝试从 DB 查
        return graphValidator.validate(graph.getElements(), formKey);
    }

    @Override
    public BpmProcessDef getDef(Long id) {
        return getExisting(id);
    }

    @Override
    public PageResult<BpmProcessDef> listDefs(PageParam pageParam) {
        LambdaQueryWrapper<BpmProcessDef> wrapper = Wrappers.<BpmProcessDef>lambdaQuery()
                .orderByDesc(BpmProcessDef::getUpdateTime);
        PageResult<BpmProcessDef> result = mapper.selectPage(pageParam, wrapper);
        // 列表不返回 graph_json 大字段
        if (result.getRecords() != null) {
            result.getRecords().forEach(r -> r.setGraphJson(null));
        }
        return result;
    }

    @Override
    @Transactional
    public void deleteDef(Long id) {
        BpmProcessDef entity = getExisting(id);
        mapper.deleteById(entity.getId());
        log.info("Soft-deleted process def: id={}", id);
    }

    @Override
    @Transactional
    public BpmProcessDef publish(Long id) {
        BpmProcessDef def = getExisting(id);

        // 解析图
        ProcessGraph graph = parseGraph(def.getGraphJson());
        if (graph == null || graph.getElements() == null || graph.getElements().isEmpty()) {
            throw new BaseException(BpmErrorCode.GRAPH_MISSING_START);
        }

        // ========== ② 发布门校验 ==========
        // 2a. 图拓扑校验（复现 GraphValidator 全规则）
        List<GraphValidationError> graphErrors = graphValidator.validate(graph.getElements(), graph.getFormKey());
        if (!graphErrors.isEmpty()) {
            // 将首条错误转为异常
            GraphValidationError first = graphErrors.get(0);
            throw new BaseException(first.getErrorCode(), first.getMessage());
        }

        // 2b. formKey 对应表单已发布（2100）
        String formKey = graph.getFormKey();
        if (formKey != null && !formKey.isBlank()) {
            FormDefDTO formDef = formDefinitionService.getFormDef(formKey);
            if (formDef == null) {
                throw new BaseException(BpmErrorCode.GRAPH_FORM_NOT_FOUND);
            }
            if (!FORM_STATUS_PUBLISHED.equals(formDef.getStatus())) {
                throw new BaseException(BpmErrorCode.FORM_NOT_PUBLISHED);
            }
        }

        // 2c. process_key 冻结检查（2101）
        // 若该 def 已有 PUBLISHED 历史，检查 process_key 是否未变
        // 首次发布时 def.getStatus() == DRAFT，跳过此检查
        String newProcessKey = graph.getProcessKey();
        if (STATUS_PUBLISHED.equals(def.getStatus())) {
            // 已发布过的定义：process_key 不可变更
            if (!def.getProcessKey().equals(newProcessKey)) {
                throw new BaseException(BpmErrorCode.PROCESS_KEY_FROZEN);
            }
        }
        // 注意：查询已有历史需额外扫库，此处仅守住已 PUBLISHED 的当前记录
        // 若存在此前已发布的版本但删了，当前为 DRAFT 的新记录，则允许新 key

        // ========== ③ 翻译 ==========
        byte[] bpmnXml = bpmDeployFacade.translateToBpmn(graph);

        // ========== ④ 部署 ==========
        String deploymentName = graph.getName() != null ? graph.getName() : newProcessKey;
        BpmDeployResult deployResult = bpmDeployFacade.deployModel(bpmnXml, deploymentName);

        // ========== ⑤ 回填 + ⑥ 状态 ==========
        def.setDeploymentId(deployResult.getDeploymentId());
        def.setProcessDefinitionId(deployResult.getProcessDefinitionId());
        def.setStatus(STATUS_PUBLISHED);
        mapper.updateById(def);

        log.info("Process def published: id={}, processKey={}, deploymentId={}, processDefinitionId={}",
                id, newProcessKey, deployResult.getDeploymentId(), deployResult.getProcessDefinitionId());

        return def;
    }

    // ==================== 内部方法 ====================

    private BpmProcessDef getExisting(Long id) {
        BpmProcessDef entity = mapper.selectById(id);
        if (entity == null) {
            throw new BaseException(BpmErrorCode.PROCESS_DEF_NOT_FOUND);
        }
        return entity;
    }

    private ProcessGraph parseGraph(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(graphJson, ProcessGraph.class);
        } catch (Exception e) {
            log.warn("Failed to parse graph_json: {}", e.getMessage());
            return null;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    /**
     * 生成初始图：START 节点 → 一条边 → END 节点，开箱即合法。
     */
    private ProcessGraph buildInitialGraph(String processKey, String name, String formKey) {
        String startId = "node_start";
        String endId = "node_end";
        String edgeId = "edge_1";

        // 默认坐标（设计器画布可调整）
        Map<String, Object> startStyle = new LinkedHashMap<>();
        startStyle.put("x", 100);
        startStyle.put("y", 300);

        Map<String, Object> endStyle = new LinkedHashMap<>();
        endStyle.put("x", 700);
        endStyle.put("y", 300);

        GraphElement startNode = GraphElement.builder()
                .id(startId)
                .kind("node")
                .type("START")
                .style(startStyle)
                .config(Collections.emptyMap())
                .build();

        GraphElement endNode = GraphElement.builder()
                .id(endId)
                .kind("node")
                .type("END")
                .style(endStyle)
                .config(Collections.emptyMap())
                .build();

        GraphElement edge = GraphElement.builder()
                .id(edgeId)
                .kind("edge")
                .source(startId)
                .target(endId)
                .style(Collections.emptyMap())
                .config(Collections.emptyMap())
                .build();

        return ProcessGraph.builder()
                .processKey(processKey)
                .name(name)
                .formKey(formKey)
                .version(1)
                .elements(List.of(startNode, endNode, edge))
                .canvas(Collections.emptyMap())
                .build();
    }
}
