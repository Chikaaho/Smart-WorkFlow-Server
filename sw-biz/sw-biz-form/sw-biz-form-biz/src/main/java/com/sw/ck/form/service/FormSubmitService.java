package com.sw.ck.form.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.common.crypto.AesGcmCipher;
import com.sw.ck.common.event.DomainEventPublisher;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.form.api.event.FormSubmittedEvent;
import com.sw.ck.form.api.exception.FormErrorCode;
import com.sw.ck.form.dynamic.DynamicTableManager;
import com.sw.ck.form.entity.*;
import com.sw.ck.form.mapper.FormConfigMapper;
import com.sw.ck.form.mapper.FormDefMapper;
import com.sw.ck.form.mapper.FormTraceMapper;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sw.ck.system.api.dict.DictFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 表单提交服务。
 * <p>
 * 完成表单提交的完整链路：
 * <ol>
 *   <li>校验（必填/类型/字典值域/未知字段）</li>
 *   <li>写入动态宽表（主表 + TABLE 子表 + REFERENCE 外键）</li>
 *   <li>写入 {@code sw_form_trace} 溯源记录</li>
 *   <li>发布 {@link FormSubmittedEvent}（经 {@link DomainEventPublisher}，供 workflow 消费）</li>
 * </ol>
 * </p>
 *
 * <h3>红线</h3>
 * <ul>
 *   <li>用户提交值一律 {@link PreparedStatement} 占位符，绝不拼入 SQL</li>
 *   <li>{@code tenant_id} 从 {@link LoginUserHolder} 取并手动写入（MyBatis-Plus 拦截器对动态宽表失效）</li>
 *   <li>动态宽表查询手动带 {@code WHERE tenant_id = ? AND deleted = 0}</li>
 * </ul>
 */
@Service
public class FormSubmitService {

    private static final Logger log = LoggerFactory.getLogger(FormSubmitService.class);

    private final FormDefMapper formDefMapper;
    private final FormConfigMapper formConfigMapper;
    private final FormTraceMapper formTraceMapper;
    private final DynamicTableManager dynamicTableManager;
    private final FormIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DictFacade dictFacade;
    private final DomainEventPublisher eventPublisher;
    private final AesGcmCipher aesCipher;

    public FormSubmitService(FormDefMapper formDefMapper,
                             FormConfigMapper formConfigMapper,
                             FormTraceMapper formTraceMapper,
                             DynamicTableManager dynamicTableManager,
                             FormIdGenerator idGenerator,
                             ObjectMapper objectMapper,
                             JdbcTemplate jdbcTemplate,
                             DictFacade dictFacade,
                             DomainEventPublisher eventPublisher,
                             Optional<AesGcmCipher> aesCipher) {
        this.formDefMapper = formDefMapper;
        this.formConfigMapper = formConfigMapper;
        this.formTraceMapper = formTraceMapper;
        this.dynamicTableManager = dynamicTableManager;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.dictFacade = dictFacade;
        this.eventPublisher = eventPublisher;
        this.aesCipher = aesCipher.orElse(null);
    }

    // ==================== 主入口 ====================

    /**
     * 提交表单数据。
     * <p>
     * 事务边界涵盖：校验 → 动态宽表写入 → 子表写入 → trace 写入，
     * 事件发布在事务内完成，由 {@code @TransactionalEventListener(AFTER_COMMIT)} 消费。
     * </p>
     *
     * @param formKey           表单业务标识
     * @param submittedData     提交数据（字段名 → 值）
     * @param submitIp          提交者 IP（AES 加密存储）
     * @param deviceFingerprint 设备指纹（有则 SHA-256 哈希）
     * @param userAgent         User-Agent 字符串
     * @return 主表记录 UUID（recordId）
     */
    @Transactional(rollbackFor = Exception.class)
    public String submitForm(String formKey,
                             Map<String, Object> submittedData,
                             String submitIp,
                             String deviceFingerprint,
                             String userAgent) {
        // ==========================================================
        // Step 1: 获取当前用户
        // ==========================================================
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null) {
            throw new BaseException(com.sw.ck.common.exception.CommonErrorCode.UNAUTHORIZED, "未登录");
        }
        Long tenantId = loginUser.getTenantId();
        Long userId = loginUser.getUserId();

        log.info("Form submit start: formKey={}, userId={}, tenantId={}", formKey, userId, tenantId);

        // ==========================================================
        // Step 2: 加载表单定义 + 校验状态
        // ==========================================================
        LambdaQueryWrapper<FormDefEntity> defQuery = Wrappers.lambdaQuery(FormDefEntity.class)
                .eq(FormDefEntity::getFormKey, formKey);
        FormDefEntity formDef = formDefMapper.selectOne(defQuery);
        if (formDef == null) {
            throw new BaseException(FormErrorCode.FORM_NOT_FOUND, "表单 '" + formKey + "' 不存在");
        }
        if (!FormStatusEnum.PUBLISHED.getCode().equals(formDef.getStatus())) {
            throw new BaseException(FormErrorCode.FORM_NOT_PUBLISHED, "表单 '" + formKey + "' 未发布，不能提交");
        }
        String tableName = formDef.getPhysicalTableName();
        if (tableName == null || tableName.isBlank()) {
            throw new BaseException(FormErrorCode.SUBMIT_FAILED, "表单 '" + formKey + "' 无物理表，无法提交");
        }

        // ==========================================================
        // Step 3: 加载表单配置并解析字段定义
        // ==========================================================
        Map<String, FieldDef> fieldDefs = loadAndParseFieldDefs(formDef.getId(), submittedData);

        // ==========================================================
        // Step 4: 校验字段（全部校验通过才落库）
        // ==========================================================
        validateFields(fieldDefs, submittedData);

        // ==========================================================
        // Step 5: 构建系统列 + 用户列值
        // ==========================================================
        String recordId = idGenerator.generate();
        Map<String, Object> systemCols = buildSystemColumns(recordId, tenantId, userId);

        // 用户列（按 fieldDefs 顺序构建，排除 TABLE 类型）
        Map<String, String> subTableMapping = parseSubTableMapping(formDef.getSubTableMapping());
        List<String> userColumns = new ArrayList<>();
        List<Object> userValues = new ArrayList<>();
        List<String> tableFieldNames = new ArrayList<>(); // TABLE 字段名列表（需单独处理）

        for (Map.Entry<String, FieldDef> entry : fieldDefs.entrySet()) {
            String fieldName = entry.getKey();
            FieldDef def = entry.getValue();

            if ("TABLE".equals(def.type)) {
                tableFieldNames.add(fieldName);
                continue; // TABLE 不在主表加列
            }

            String colName = convertToColumnName(fieldName, def.type);
            Object value = submittedData.get(fieldName);

            // BOOL 类型转换：true/false → 1/0
            if ("BOOL".equals(def.type)) {
                value = convertBoolValue(value);
            }

            userColumns.add(colName);
            userValues.add(value);
        }

        // ==========================================================
        // Step 6: INSERT 主表
        // ==========================================================
        List<String> allColumns = new ArrayList<>(systemCols.keySet());
        List<Object> allValues = new ArrayList<>(systemCols.values());
        allColumns.addAll(userColumns);
        allValues.addAll(userValues);

        String insertSql = buildInsertSql(tableName, allColumns);
        jdbcTemplate.update(insertSql, allValues.toArray());
        log.debug("Inserted main record: table={}, recordId={}", tableName, recordId);

        // ==========================================================
        // Step 7: 处理 TABLE 子表
        // ==========================================================
        for (String tableFieldName : tableFieldNames) {
            String subTableName = subTableMapping.get(tableFieldName);
            if (subTableName == null) {
                log.warn("No sub-table mapping for TABLE field '{}', skipping", tableFieldName);
                continue;
            }

            Object rawValue = submittedData.get(tableFieldName);
            if (rawValue == null) {
                continue;
            }

            List<Map<String, Object>> rows;
            if (rawValue instanceof List<?> rawList) {
                rows = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof Map<?, ?> itemMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typedMap = (Map<String, Object>) itemMap;
                        rows.add(typedMap);
                    }
                }
            } else {
                log.warn("TABLE field '{}' value is not a List, skipping", tableFieldName);
                continue;
            }

            // 获取子表字段定义
            FieldDef tableFieldDef = fieldDefs.get(tableFieldName);
            List<String> subUserColumns = new ArrayList<>();
            if (tableFieldDef != null && tableFieldDef.subFields != null) {
                for (FieldDef subDef : tableFieldDef.subFields) {
                    subUserColumns.add(convertToColumnName(subDef.name, subDef.type));
                }
            }

            for (Map<String, Object> row : rows) {
                String subRecordId = idGenerator.generate();
                Map<String, Object> subSysCols = buildSystemColumns(subRecordId, tenantId, userId);
                subSysCols.put("parent_record_id", recordId);

                List<String> subCols = new ArrayList<>(subSysCols.keySet());
                List<Object> subVals = new ArrayList<>(subSysCols.values());

                for (int i = 0; i < subUserColumns.size(); i++) {
                    subCols.add(subUserColumns.get(i));
                    // BOOL 类型转换
                    String subFieldName = tableFieldDef.subFields.get(i).name;
                    String subFieldType = tableFieldDef.subFields.get(i).type;
                    Object val = row.get(subFieldName);
                    if ("BOOL".equals(subFieldType)) {
                        val = convertBoolValue(val);
                    }
                    subVals.add(val);
                }

                String subInsertSql = buildInsertSql(subTableName, subCols);
                jdbcTemplate.update(subInsertSql, subVals.toArray());
            }
            log.debug("Inserted {} rows into sub-table '{}' for field '{}'", rows.size(), subTableName, tableFieldName);
        }

        // ==========================================================
        // Step 8: 写入 sw_form_trace
        // ==========================================================
        FormTraceEntity trace = new FormTraceEntity();
        trace.setId(idGenerator.generate());
        trace.setFormId(formDef.getId());
        trace.setRecordId(recordId);
        trace.setSubmitUserId(userId);
        trace.setSubmitIp(encryptIp(submitIp));
        trace.setSubmitTime(LocalDateTime.now());
        trace.setDeviceFingerprint(deviceFingerprint);
        trace.setUserAgent(userAgent);
        trace.setTenantId(tenantId);
        trace.setDeleted(0);
        trace.setCreateTime(LocalDateTime.now());
        trace.setCreateBy(userId);
        trace.setUpdateTime(LocalDateTime.now());
        trace.setUpdateBy(userId);
        trace.setVersion(0L);
        formTraceMapper.insert(trace);
        log.debug("Inserted trace record: formId={}, recordId={}", formDef.getId(), recordId);

        // ==========================================================
        // Step 9: 发布 FormSubmittedEvent
        // ==========================================================
        String submitterStr = String.valueOf(userId);
        FormSubmittedEvent event = new FormSubmittedEvent(formKey, submittedData, submitterStr, recordId, tenantId);
        eventPublisher.publish(event);
        log.info("Form submit completed: formKey={}, recordId={}, submitter={}",
                formKey, recordId, submitterStr);

        return recordId;
    }

    // ==================== 校验 ====================

    /**
     * 解析 form definition JSON，提取字段定义并同步校验未知字段。
     *
     * @param formId        表单 ID
     * @param submittedData 提交数据（用于检测未定义字段）
     * @return 字段名 → FieldDef 映射
     */
    private Map<String, FieldDef> loadAndParseFieldDefs(String formId, Map<String, Object> submittedData) {
        // 从 sw_form_config 加载 definition JSON
        LambdaQueryWrapper<FormConfigEntity> configQuery = Wrappers.lambdaQuery(FormConfigEntity.class)
                .eq(FormConfigEntity::getFormId, formId);
        FormConfigEntity config = formConfigMapper.selectOne(configQuery);
        String definitionJson = (config != null) ? config.getDefinition() : null;

        if (definitionJson == null || definitionJson.isBlank() || "{}".equals(definitionJson)) {
            log.warn("Form config definition is empty for formId={}, skip field validation", formId);
            return new HashMap<>();
        }

        try {
            JsonNode root = objectMapper.readTree(definitionJson);
            JsonNode fieldsArray = root.get("fields");
            if (fieldsArray == null || !fieldsArray.isArray() || fieldsArray.isEmpty()) {
                log.warn("Form definition has no 'fields' array for formId={}, skip field validation", formId);
                return new HashMap<>();
            }

            Map<String, FieldDef> fieldDefs = new LinkedHashMap<>();
            for (JsonNode fieldNode : fieldsArray) {
                JsonNode nameNode = fieldNode.get("name");
                if (nameNode == null || nameNode.asText().isBlank()) continue;

                String name = nameNode.asText();
                String type = fieldNode.has("type") ? fieldNode.get("type").asText() : "TEXT";
                boolean required = fieldNode.has("required") && fieldNode.get("required").asBoolean();
                String dictType = fieldNode.has("dictType") ? fieldNode.get("dictType").asText() : null;

                // 解析 TABLE 子字段
                List<FieldDef> subFields = null;
                if ("TABLE".equals(type) && fieldNode.has("subFields")) {
                    JsonNode subArray = fieldNode.get("subFields");
                    if (subArray.isArray()) {
                        subFields = new ArrayList<>();
                        for (JsonNode sub : subArray) {
                            String subName = sub.has("name") ? sub.get("name").asText() : null;
                            if (subName == null) continue;
                            String subType = sub.has("type") ? sub.get("type").asText() : "TEXT";
                            subFields.add(new FieldDef(subName, subType, false, null, null));
                        }
                    }
                }

                fieldDefs.put(name, new FieldDef(name, type, required, dictType, subFields));
            }

            // 检查未知字段
            for (String submittedField : submittedData.keySet()) {
                if (!fieldDefs.containsKey(submittedField)) {
                    throw new BaseException(FormErrorCode.SUBMIT_FIELD_UNKNOWN,
                            "提交了未定义的字段: '" + submittedField + "'");
                }
            }

            return fieldDefs;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse form definition JSON for formId={}", formId, e);
            throw new BaseException(FormErrorCode.SUBMIT_DEFINITION_INVALID, "表单定义配置解析失败");
        }
    }

    /**
     * 校验字段值（必填、类型、字典值域）。
     * <p>
     * 全部校验通过才返回；任一失败抛 {@link BaseException}。
     * </p>
     */
    private void validateFields(Map<String, FieldDef> fieldDefs, Map<String, Object> submittedData) {
        if (fieldDefs.isEmpty()) {
            return; // 无定义时不校验
        }

        for (FieldDef def : fieldDefs.values()) {
            Object value = submittedData.get(def.name);

            // —— 必填校验 ——
            if (def.required) {
                boolean isEmpty = value == null
                        || (value instanceof String s && s.isBlank());
                if ("TABLE".equals(def.type)) {
                    // TABLE 类型检查是否为空列表
                    isEmpty = value == null
                            || (value instanceof List<?> list && list.isEmpty());
                }
                if (isEmpty) {
                    throw new BaseException(FormErrorCode.SUBMIT_FIELD_REQUIRED,
                            "必填字段 '" + def.name + "' 缺失");
                }
            }

            // 空值免后续校验
            if (value == null || (value instanceof String s && s.isBlank())) {
                continue;
            }

            // —— 类型校验 ——
            switch (def.type) {
                case "NUMBER" -> {
                    if (!(value instanceof Number)) {
                        // 尝试从字符串解析
                        if (value instanceof String s) {
                            try {
                                new java.math.BigDecimal(s);
                            } catch (NumberFormatException e) {
                                throw new BaseException(FormErrorCode.SUBMIT_FIELD_TYPE_MISMATCH,
                                        "字段 '" + def.name + "' 需要数字类型，实际值: '" + s + "'");
                            }
                        } else {
                            throw new BaseException(FormErrorCode.SUBMIT_FIELD_TYPE_MISMATCH,
                                    "字段 '" + def.name + "' 需要数字类型");
                        }
                    }
                }
                case "DATE" -> {
                    // 接受字符串或 Long(时间戳)
                    if (!(value instanceof String) && !(value instanceof Number)) {
                        throw new BaseException(FormErrorCode.SUBMIT_FIELD_TYPE_MISMATCH,
                                "字段 '" + def.name + "' 需要日期类型");
                    }
                }
                case "BOOL" -> {
                    // 最终会转 0/1，能转即可
                    Object converted = convertBoolValue(value);
                    if (converted == null) {
                        throw new BaseException(FormErrorCode.SUBMIT_FIELD_TYPE_MISMATCH,
                                "字段 '" + def.name + "' 需要布尔类型");
                    }
                }
                case "DICT" -> {
                    String dictType = def.dictType;
                    if (dictType == null || dictType.isBlank()) {
                        log.warn("DICT field '{}' has no dictType, skip dict validation", def.name);
                    } else {
                        String code = String.valueOf(value);
                        boolean valid = dictFacade.isValidCode(dictType, code);
                        if (!valid) {
                            throw new BaseException(FormErrorCode.SUBMIT_DICT_INVALID,
                                    "字典字段 '" + def.name + "' 的值 '" + code + "' 不在字典类型 '" + dictType + "' 的值域内");
                        }
                    }
                }
                // TEXT / RICH_TEXT / REFERENCE 无额外校验
            }
        }
    }

    // ==================== 系统列填充 ====================

    /**
     * 构建动态宽表的系统列键值对（MyBatis-Plus 拦截器对动态宽表失效，需手动填充）。
     * <p>
     * 主表和子表共用此方法。
     * </p>
     *
     * @param recordId 记录 UUID
     * @param tenantId 当前租户 ID
     * @param userId   当前用户 ID
     * @return 有序的列名→值映射
     */
    Map<String, Object> buildSystemColumns(String recordId, Long tenantId, Long userId) {
        Map<String, Object> cols = new LinkedHashMap<>();
        cols.put("id", recordId);
        cols.put("tenant_id", tenantId);
        cols.put("deleted", 0);
        cols.put("create_time", LocalDateTime.now());
        cols.put("create_by", userId);
        cols.put("update_time", LocalDateTime.now());
        cols.put("update_by", userId);
        cols.put("version", 0L);
        return cols;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 构建 INSERT SQL（PreparedStatement 占位符）。
     */
    private String buildInsertSql(String tableName, List<String> columns) {
        String quotedCols = columns.stream()
                .map(c -> "\"" + c + "\"")
                .collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO \"" + tableName + "\" (" + quotedCols + ") VALUES (" + placeholders + ")";
    }

    /**
     * 字段名列转换：REFERENCE → {@code ref_{name}_id}，其余原值。
     */
    private String convertToColumnName(String fieldName, String type) {
        if ("REFERENCE".equals(type)) {
            return "ref_" + fieldName + "_id";
        }
        return fieldName;
    }

    /**
     * BOOL 值转换：true/false/"true"/"false"/1/0 → 1/0（SMALLINT）。
     *
     * @return Integer 1 或 0；无法转换返回 null
     */
    private Integer convertBoolValue(Object value) {
        if (value == null) return 0;
        if (value instanceof Boolean b) return b ? 1 : 0;
        if (value instanceof Number n) return n.intValue() != 0 ? 1 : 0;
        if (value instanceof String s) {
            return switch (s.trim().toLowerCase()) {
                case "true", "1", "yes", "on" -> 1;
                case "false", "0", "no", "off", "" -> 0;
                default -> null; // 无法识别
            };
        }
        return null;
    }

    /**
     * 解析子表映射 JSON。
     *
     * @param subTableMappingJson FormDefEntity.subTableMapping 的 JSON 字串
     * @return 字段名 → 子表名映射
     */
    private Map<String, String> parseSubTableMapping(String subTableMappingJson) {
        if (subTableMappingJson == null || subTableMappingJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(subTableMappingJson,
                    new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse sub-table mapping JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * AES 加密 IP 地址；加密失败时回退明文存储。
     */
    private String encryptIp(String ip) {
        if (ip == null || ip.isBlank()) return ip;
        if (aesCipher == null) {
            log.warn("AesGcmCipher not configured, storing IP in plaintext");
            return ip;
        }
        try {
            return aesCipher.encrypt(ip);
        } catch (Exception e) {
            log.warn("IP encryption failed, storing as plaintext: {}", e.getMessage());
            return ip;
        }
    }

    // ==================== 内部类型 ====================

    /**
     * 表单字段定义（从 definition JSON 解析）。
     */
    private record FieldDef(
            String name,
            String type,
            boolean required,
            String dictType,
            List<FieldDef> subFields
    ) {}
}
