package com.sw.ck.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.form.api.dto.FormDefDTO;
import com.sw.ck.form.api.exception.FormErrorCode;
import com.sw.ck.form.dynamic.DynamicTableManager;
import com.sw.ck.form.dynamic.FieldSpec;
import com.sw.ck.form.dynamic.FieldType;
import com.sw.ck.form.dynamic.FormTableSpec;
import com.sw.ck.form.entity.*;
import com.sw.ck.form.mapper.FormConfigMapper;
import com.sw.ck.form.mapper.FormDefMapper;
import com.sw.ck.form.mapper.FormSnapshotMapper;
import com.sw.ck.form.service.FormDefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 表单定义管理服务实现。
 */
@Service
public class FormDefServiceImpl implements FormDefService {

    private static final Logger log = LoggerFactory.getLogger(FormDefServiceImpl.class);

    private final FormDefMapper formDefMapper;
    private final FormConfigMapper formConfigMapper;
    private final FormSnapshotMapper formSnapshotMapper;
    private final DynamicTableManager dynamicTableManager;
    private final FormIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public FormDefServiceImpl(FormDefMapper formDefMapper,
                              FormConfigMapper formConfigMapper,
                              FormSnapshotMapper formSnapshotMapper,
                              DynamicTableManager dynamicTableManager,
                              FormIdGenerator idGenerator,
                              ObjectMapper objectMapper) {
        this.formDefMapper = formDefMapper;
        this.formConfigMapper = formConfigMapper;
        this.formSnapshotMapper = formSnapshotMapper;
        this.dynamicTableManager = dynamicTableManager;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDefDTO createDraft(String formKey, String name, String logicalTableName, String description) {
        // —— 校验唯一性 ——
        LambdaQueryWrapper<FormDefEntity> keyQuery = Wrappers.lambdaQuery(FormDefEntity.class)
                .eq(FormDefEntity::getFormKey, formKey);
        if (formDefMapper.selectCount(keyQuery) > 0) {
            throw new BaseException(FormErrorCode.FORM_KEY_DUPLICATE, "表单标识 '" + formKey + "' 已存在");
        }

        // —— 创建草稿 ——
        FormDefEntity entity = new FormDefEntity();
        entity.setId(idGenerator.generate());
        entity.setFormKey(formKey);
        entity.setName(name);
        entity.setLogicalTableName(logicalTableName);
        entity.setDescription(description);
        entity.setStatus(FormStatusEnum.DRAFT.getCode());
        entity.setFormVersion(1);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setTenantId(0L);
        entity.setDeleted(0);
        entity.setVersion(0L);
        formDefMapper.insert(entity);

        // —— 创建空白 config ——
        FormConfigEntity config = new FormConfigEntity();
        config.setId(idGenerator.generate());
        config.setFormId(entity.getId());
        config.setDefinition("{}");
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        config.setTenantId(0L);
        config.setDeleted(0);
        config.setVersion(0L);
        formConfigMapper.insert(config);

        log.info("Created form draft: id={}, formKey={}", entity.getId(), formKey);
        return toDTO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDefDTO updateDraft(String id, String name, String logicalTableName, String description) {
        FormDefEntity entity = formDefMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(FormErrorCode.FORM_NOT_FOUND);
        }
        if (!FormStatusEnum.DRAFT.getCode().equals(entity.getStatus())) {
            throw new BaseException(FormErrorCode.FORM_ALREADY_PUBLISHED, "已发布的表单不能修改元数据");
        }

        entity.setName(name);
        entity.setLogicalTableName(logicalTableName);
        entity.setDescription(description);
        entity.setUpdateTime(LocalDateTime.now());
        formDefMapper.updateById(entity);

        return toDTO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(String formId, String definition) {
        FormDefEntity entity = formDefMapper.selectById(formId);
        if (entity == null) {
            throw new BaseException(FormErrorCode.FORM_NOT_FOUND);
        }

        LambdaQueryWrapper<FormConfigEntity> query = Wrappers.lambdaQuery(FormConfigEntity.class)
                .eq(FormConfigEntity::getFormId, formId);
        FormConfigEntity config = formConfigMapper.selectOne(query);
        if (config == null) {
            // 创建新的 config 记录
            config = new FormConfigEntity();
            config.setId(idGenerator.generate());
            config.setFormId(formId);
            config.setDefinition(definition);
            config.setCreateTime(LocalDateTime.now());
            config.setUpdateTime(LocalDateTime.now());
            config.setTenantId(0L);
            config.setDeleted(0);
            config.setVersion(0L);
            formConfigMapper.insert(config);
        } else {
            config.setDefinition(definition);
            config.setUpdateTime(LocalDateTime.now());
            formConfigMapper.updateById(config);
        }

        log.info("Saved form config: formId={}", formId);
    }

    @Override
    public void publish(String formId, String fieldSpecs) {
        // —— Step 1: 加载并校验状态 ——
        FormDefEntity entity = formDefMapper.selectById(formId);
        if (entity == null) {
            throw new BaseException(FormErrorCode.FORM_NOT_FOUND);
        }
        if (!FormStatusEnum.DRAFT.getCode().equals(entity.getStatus())) {
            throw new BaseException(FormErrorCode.FORM_ALREADY_PUBLISHED, "表单已发布，不能重复发布");
        }

        // —— Step 2: 解析 fieldSpecs JSON ——
        List<FieldSpec> fields = parseFieldSpecs(fieldSpecs);

        // —— Step 3: 校验字段名白名单（复用 DynamicTableManager.validateColumnName） ——
        // 逻辑表名校验（如果有）
        if (entity.getLogicalTableName() != null && !entity.getLogicalTableName().isBlank()) {
            dynamicTableManager.validateColumnName(entity.getLogicalTableName());
        }
        // 每个字段名校验
        Set<String> columnNames = new HashSet<>();
        for (FieldSpec field : fields) {
            String physicalName;
            try {
                physicalName = field.getPhysicalColumnName();
            } catch (IllegalStateException e) {
                // TABLE 类型没有 physical column name，跳过
                continue;
            }
            try {
                dynamicTableManager.validateColumnName(physicalName);
            } catch (IllegalArgumentException e) {
                throw new BaseException(FormErrorCode.INVALID_COLUMN_NAME, "字段名不合法: '" + physicalName + "' — " + e.getMessage());
            }
            // 重复字段名检查
            if (!columnNames.add(physicalName)) {
                throw new BaseException(FormErrorCode.DUPLICATE_COLUMN, "字段名重复: '" + physicalName + "'");
            }
        }

        // —— Step 4: 创建物理表（DDL 不可回滚，因此校验先行） ——
        FormTableSpec tableSpec = new FormTableSpec(false, fields);
        Map<String, String> subTableNameSink = new HashMap<>();
        String physicalTableName;
        try {
            physicalTableName = dynamicTableManager.createFormTable(tableSpec, subTableNameSink);
        } catch (Exception e) {
            log.error("Failed to create physical table for form: {}", formId, e);
            throw new BaseException(FormErrorCode.PUBLISH_FAILED, "创建动态宽表失败: " + e.getMessage());
        }
        log.info("Physical table created: {} for form: {}", physicalTableName, formId);

        // —— Step 4a: 序列化子表映射 ——
        String subTableMappingJson = null;
        if (!subTableNameSink.isEmpty()) {
            try {
                subTableMappingJson = objectMapper.writeValueAsString(subTableNameSink);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize sub-table mapping, skip: {}", e.getMessage());
            }
        }

        // —— Step 5: 更新表单元数据 ——
        entity.setPhysicalTableName(physicalTableName);
        entity.setStatus(FormStatusEnum.PUBLISHED.getCode());
        entity.setFormVersion(entity.getFormVersion() == null ? 1 : entity.getFormVersion() + 1);
        entity.setSubTableMapping(subTableMappingJson);
        entity.setUpdateTime(LocalDateTime.now());
        formDefMapper.updateById(entity);

        // —— Step 6: 存快照 ——
        LambdaQueryWrapper<FormConfigEntity> configQuery = Wrappers.lambdaQuery(FormConfigEntity.class)
                .eq(FormConfigEntity::getFormId, formId);
        FormConfigEntity config = formConfigMapper.selectOne(configQuery);
        String definitionJson = (config != null) ? config.getDefinition() : "{}";

        FormSnapshotEntity snapshot = new FormSnapshotEntity();
        snapshot.setId(idGenerator.generate());
        snapshot.setFormId(formId);
        snapshot.setFormVersion(entity.getFormVersion());
        snapshot.setDefinition(definitionJson);
        snapshot.setCreateTime(LocalDateTime.now());
        snapshot.setUpdateTime(LocalDateTime.now());
        snapshot.setTenantId(0L);
        snapshot.setDeleted(0);
        snapshot.setVersion(0L);
        formSnapshotMapper.insert(snapshot);

        log.info("Form published: id={}, formKey={}, physicalTable={}, version={}",
                formId, entity.getFormKey(), physicalTableName, entity.getFormVersion());
    }

    @Override
    public FormDefDTO getFormDef(String id) {
        FormDefEntity entity = formDefMapper.selectById(id);
        return entity != null ? toDTO(entity) : null;
    }

    @Override
    public FormDefDTO getFormDefByKey(String formKey) {
        LambdaQueryWrapper<FormDefEntity> query = Wrappers.lambdaQuery(FormDefEntity.class)
                .eq(FormDefEntity::getFormKey, formKey);
        FormDefEntity entity = formDefMapper.selectOne(query);
        return entity != null ? toDTO(entity) : null;
    }

    @Override
    public String getDefinition(String formKey) {
        LambdaQueryWrapper<FormDefEntity> defQuery = Wrappers.lambdaQuery(FormDefEntity.class)
                .eq(FormDefEntity::getFormKey, formKey);
        FormDefEntity entity = formDefMapper.selectOne(defQuery);
        if (entity == null) {
            return null;
        }
        LambdaQueryWrapper<FormConfigEntity> configQuery = Wrappers.lambdaQuery(FormConfigEntity.class)
                .eq(FormConfigEntity::getFormId, entity.getId());
        FormConfigEntity config = formConfigMapper.selectOne(configQuery);
        return config != null ? config.getDefinition() : null;
    }

    @Override
    public String getDefinitionById(String formId) {
        LambdaQueryWrapper<FormConfigEntity> configQuery = Wrappers.lambdaQuery(FormConfigEntity.class)
                .eq(FormConfigEntity::getFormId, formId);
        FormConfigEntity config = formConfigMapper.selectOne(configQuery);
        return config != null ? config.getDefinition() : null;
    }

    @Override
    public FormDefEntity getById(String id) {
        return formDefMapper.selectById(id);
    }

    // ==================== 内部方法 ====================

    private FormDefDTO toDTO(FormDefEntity entity) {
        return FormDefDTO.builder()
                .id(entity.getId())
                .formKey(entity.getFormKey())
                .name(entity.getName())
                .logicalTableName(entity.getLogicalTableName())
                .status(entity.getStatus())
                .physicalTableName(entity.getPhysicalTableName())
                .formVersion(entity.getFormVersion())
                .description(entity.getDescription())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    /**
     * 解析前端提交的字段规格 JSON 为 FieldSpec 列表。
     * <p>
     * 期望 JSON 格式：
     * <pre>
     * [
     *   {"name": "full_name", "type": "TEXT"},
     *   {"name": "age", "type": "NUMBER"},
     *   {"name": "gender", "type": "DICT", "dictType": "sys_user_sex"},
     *   {"name": "dept", "type": "REFERENCE", "targetFormId": "it_application"},
     *   {"name": "items", "type": "TABLE", "subFields": [
     *     {"name": "item_name", "type": "TEXT"},
     *     {"name": "qty", "type": "NUMBER"}
     *   ]}
     * ]
     * </pre>
     */
    List<FieldSpec> parseFieldSpecs(String json) {
        if (json == null || json.isBlank()) {
            throw new BaseException(FormErrorCode.INVALID_COLUMN_NAME, "字段规格不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                throw new BaseException(FormErrorCode.INVALID_COLUMN_NAME, "字段规格应为 JSON 数组");
            }
            List<FieldSpec> fields = new ArrayList<>();
            for (JsonNode node : root) {
                fields.add(parseFieldNode(node));
            }
            return fields;
        } catch (JsonProcessingException e) {
            throw new BaseException(FormErrorCode.INVALID_COLUMN_NAME, "字段规格 JSON 解析失败: " + e.getMessage());
        }
    }

    private FieldSpec parseFieldNode(JsonNode node) {
        String name = node.get("name").asText();
        String type = node.get("type").asText();
        FieldType fieldType = FieldType.valueOf(type);

        return switch (fieldType) {
            case TEXT -> FieldSpec.text(name);
            case RICH_TEXT -> FieldSpec.richText(name);
            case NUMBER -> FieldSpec.number(name);
            case DATE -> FieldSpec.date(name);
            case BOOL -> FieldSpec.bool(name);
            case DICT -> {
                String dictType = node.get("dictType").asText();
                yield FieldSpec.dict(name, dictType);
            }
            case REFERENCE -> {
                String targetFormId = node.get("targetFormId").asText();
                yield FieldSpec.ref(name, targetFormId);
            }
            case TABLE -> {
                JsonNode subArray = node.get("subFields");
                List<FieldSpec> subFields = new ArrayList<>();
                if (subArray != null && subArray.isArray()) {
                    for (JsonNode sub : subArray) {
                        subFields.add(parseFieldNode(sub));
                    }
                }
                yield FieldSpec.table(name, subFields);
            }
        };
    }
}
