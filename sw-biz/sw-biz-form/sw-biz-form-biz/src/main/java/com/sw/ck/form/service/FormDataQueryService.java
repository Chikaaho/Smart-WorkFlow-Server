package com.sw.ck.form.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.form.api.dto.FilterOp;
import com.sw.ck.form.api.dto.FormDataFilter;
import com.sw.ck.form.api.dto.FormDataQueryRequest;
import com.sw.ck.form.api.dto.FormDefDTO;
import com.sw.ck.form.api.exception.FormErrorCode;
import com.sw.ck.form.dynamic.ColumnValidation;
import com.sw.ck.form.dynamic.FieldType;
import com.sw.ck.form.entity.FormConfigEntity;
import com.sw.ck.form.mapper.FormConfigMapper;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 表单数据查询服务。
 *
 * <p>对已发布表单的动态宽表执行条件分页查询。
 * 裸 JDBC 查询（不依赖 MyBatis-Plus 拦截器），
 * 因此手动编写 {@code WHERE deleted = 0 AND tenant_id = ?}。</p>
 *
 * <h3>红线</h3>
 * <ul>
 *   <li>列名/表名过白名单（{@link ColumnValidation#physicalColumnName(String, FieldType)}）</li>
 *   <li>值一律 PreparedStatement ? 参数化绑定</li>
 *   <li>绝不 SELECT *，显式枚举列</li>
 *   <li>绝不复用 IPage（拦截器对裸 JdbcTemplate 失效）</li>
 * </ul>
 */
@Service
public class FormDataQueryService {

    private static final Logger log = LoggerFactory.getLogger(FormDataQueryService.class);

    /** 分页硬上限 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 默认分页大小（对齐 PageParam 默认值） */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 表名校验正则（对齐 DynamicTableManager.generateTableName 的 assert 模式） */
    private static final String TABLE_NAME_PATTERN = "^sw_form(_table)?_[a-z][a-z0-9]{9}$";

    // ==================== op × type 合法矩阵 ====================

    private static final Map<FieldType, Set<FilterOp>> ALLOWED_OPS = Map.of(
            FieldType.TEXT, Set.of(FilterOp.EQ, FilterOp.LIKE),
            FieldType.NUMBER, Set.of(FilterOp.EQ, FilterOp.GE, FilterOp.LE),
            FieldType.DATE, Set.of(FilterOp.EQ, FilterOp.GE, FilterOp.LE),
            FieldType.BOOL, Set.of(FilterOp.EQ),
            FieldType.DICT, Set.of(FilterOp.EQ),
            FieldType.REFERENCE, Set.of(FilterOp.EQ)
    );

    // ==================== 非可筛选类型 ====================

    private static final Set<FieldType> NON_FILTERABLE_TYPES = Set.of(
            FieldType.TABLE, FieldType.RICH_TEXT
    );

    // ==================== 系统列（以 DynamicTableManager.SYSTEM_COLUMNS 为准） ====================

    static final List<String> SYSTEM_COLUMNS = List.of(
            "id", "tenant_id", "deleted", "create_time", "create_by",
            "update_time", "update_by", "version"
    );

    // ==================== 依赖 ====================

    private final FormDefService formDefService;
    private final FormConfigMapper formConfigMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 已确保 deleted 列存在的表名集合（幂等回填缓存） */
    private final Set<String> deletedColumnEnsured = ConcurrentHashMap.newKeySet();

    public FormDataQueryService(FormDefService formDefService,
                                FormConfigMapper formConfigMapper,
                                JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper) {
        this.formDefService = formDefService;
        this.formConfigMapper = formConfigMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================== 主入口 ====================

    /**
     * 查询表单数据（分页 + 过滤）。
     *
     * @param formKey 表单业务标识
     * @param request 查询请求（分页 + 过滤条件）
     * @return 分页结果
     */
    public PageResult<Map<String, Object>> queryFormData(String formKey, FormDataQueryRequest request) {
        // —— Step 1: 获取当前用户 ——
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null) {
            throw new BaseException(com.sw.ck.common.exception.CommonErrorCode.UNAUTHORIZED, "未登录");
        }
        Long tenantId = loginUser.getTenantId();

        // —— Step 2: 解析 formKey → FormDefDTO（含 status + physicalTableName） ——
        FormDefDTO formDef = formDefService.getFormDefByKey(formKey);
        if (formDef == null) {
            throw new BaseException(FormErrorCode.QUERY_FORM_NOT_EXIST, "表单 '" + formKey + "' 不存在");
        }
        if (!"PUBLISHED".equals(formDef.getStatus())) {
            throw new BaseException(FormErrorCode.QUERY_FORM_NOT_EXIST, "表单 '" + formKey + "' 未发布，不能查询");
        }
        String tableName = formDef.getPhysicalTableName();
        if (tableName == null || tableName.isBlank()) {
            throw new BaseException(FormErrorCode.QUERY_FORM_NOT_EXIST, "表单 '" + formKey + "' 无物理表");
        }

        // —— Step 2.5: 表名防御性校验 ——
        validateTableName(tableName);

        // —— Step 3: 加载 definition JSON 并解析字段类型 ——
        Map<String, FieldType> fieldTypeMap = loadFieldTypeMap(formDef.getId());

        // —— Step 4: 校验过滤条件 ——
        List<FilterClause> clauses = validateAndBuildClauses(request.getFilters(), fieldTypeMap);

        // —— Step 5: 构建列投影 ——
        List<String> projectionColumns = buildProjection(fieldTypeMap);

        // —— Step 6: 钳制分页参数 ——
        int page = Math.max(1, (int) request.getPageNum());
        int size = clampSize((int) request.getPageSize());
        int offset = (page - 1) * size;

        // —— Step 7: 构建 WHERE 子句 ——
        StringBuilder whereBuilder = new StringBuilder();
        List<Object> filterParams = new ArrayList<>();

        whereBuilder.append("\"deleted\" = 0");
        whereBuilder.append(" AND \"tenant_id\" = ?");
        filterParams.add(tenantId);

        for (FilterClause clause : clauses) {
            whereBuilder.append(" AND ").append(clause.sql());
            filterParams.add(clause.value());
        }

        String whereSql = whereBuilder.toString();

        // —— Step 8: COUNT 查询 ——
        String countSql = "SELECT COUNT(*) FROM \"" + tableName + "\" WHERE " + whereSql;
        Long total;
        try {
            total = jdbcTemplate.queryForObject(countSql, Long.class, filterParams.toArray());
        } catch (Exception e) {
            log.error("Count query failed: table={}, sql={}", tableName, countSql, e);
            throw new BaseException(FormErrorCode.QUERY_FORM_NOT_EXIST, "查询失败: " + e.getMessage());
        }
        if (total == null) total = 0L;

        // —— Step 9: 数据查询 ——
        String columns = projectionColumns.stream()
                .map(c -> "\"" + c + "\"")
                .collect(Collectors.joining(", "));
        String dataSql = "SELECT " + columns + " FROM \"" + tableName + "\" WHERE " + whereSql
                + " ORDER BY \"create_time\" DESC LIMIT ? OFFSET ?";

        List<Object> dataParams = new ArrayList<>(filterParams);
        dataParams.add((long) size);
        dataParams.add((long) offset);

        List<Map<String, Object>> records;
        try {
            records = jdbcTemplate.queryForList(dataSql, dataParams.toArray());
        } catch (Exception e) {
            log.error("Data query failed: table={}, sql={}", tableName, dataSql, e);
            throw new BaseException(FormErrorCode.QUERY_FORM_NOT_EXIST, "查询失败: " + e.getMessage());
        }

        // —— Step 10: 构建 PageResult ——
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setRecords(records != null ? records : List.of());
        result.setTotal(total);
        result.setPageNum(page);
        result.setPageSize(size);
        return result;
    }

    // ==================== 表名校验 ====================

    /**
     * 防御性表名校验（表名来自注册表，发布期已校验，此处为纵深防御）。
     */
    private void validateTableName(String tableName) {
        if (!tableName.matches(TABLE_NAME_PATTERN)) {
            log.error("Table name '{}' does not match expected pattern '{}'", tableName, TABLE_NAME_PATTERN);
            throw new BaseException(FormErrorCode.QUERY_FORM_NOT_EXIST, "表名格式异常");
        }
    }

    // ==================== deleted 列回填 ====================

    /**
     * 幂等确保指定宽表存在 deleted 列。
     * <p>
     * 虽然 DynamicTableManager 模板始终包含 deleted 列，
     * 但若模板变更加列之前已有旧表，则需回填。
     * 先查 information_schema，缺失时 ALTER ADD COLUMN。
     * </p>
     */
    private void ensureDeletedColumn(String tableName) {
        if (!deletedColumnEnsured.add(tableName)) {
            return; // 本进程已检查过
        }

        try {
            // H2 用小写，PG 用小写 — 统一查小写
            String checkSql = "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = 'deleted'";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, tableName);
            if (count != null && count > 0) {
                return; // 已存在
            }

            log.warn("Table '{}' missing 'deleted' column, backfilling...", tableName);
            String alterSql = "ALTER TABLE \"" + tableName
                    + "\" ADD COLUMN \"deleted\" SMALLINT NOT NULL DEFAULT 0";
            jdbcTemplate.execute(alterSql);
            log.info("Backfilled 'deleted' column on table '{}'", tableName);
        } catch (Exception e) {
            log.warn("Failed to ensure 'deleted' column on table '{}': {}", tableName, e.getMessage());
            // 不阻断查询 — 如果表本身就没 deleted 列，查询 SQL 会报错，
            // 届时由 catch 块包装为业务异常
        }
    }

    // ==================== 字段类型解析 ====================

    /**
     * 从 sw_form_config.definition JSON 解析字段名 → FieldType 映射。
     */
    private Map<String, FieldType> loadFieldTypeMap(String formId) {
        // 查主表 definition（parent_table IS NULL）
        List<FormConfigEntity> configs = formConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FormConfigEntity>()
                        .eq(FormConfigEntity::getFormId, formId)
                        .isNull(FormConfigEntity::getParentTable)
        );
        FormConfigEntity config = (configs != null && !configs.isEmpty()) ? configs.get(0) : null;
        String definitionJson = (config != null) ? config.getDefinition() : null;

        if (definitionJson == null || definitionJson.isBlank() || "{}".equals(definitionJson)) {
            log.warn("Form definition is empty for formId={}", formId);
            return Map.of();
        }

        try {
            JsonNode root = objectMapper.readTree(definitionJson);
            JsonNode fieldsArray = root.get("fields");
            if (fieldsArray == null || !fieldsArray.isArray()) {
                return Map.of();
            }

            Map<String, FieldType> map = new LinkedHashMap<>();
            for (JsonNode fieldNode : fieldsArray) {
                JsonNode nameNode = fieldNode.get("name");
                if (nameNode == null || nameNode.asText().isBlank()) continue;

                String name = nameNode.asText();
                String typeStr = fieldNode.has("type") ? fieldNode.get("type").asText() : "TEXT";

                FieldType fieldType;
                try {
                    fieldType = FieldType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown field type '{}' for field '{}', skipping", typeStr, name);
                    continue;
                }

                map.put(name, fieldType);
            }
            return map;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse definition JSON for formId={}", formId, e);
            return Map.of();
        }
    }

    // ==================== 过滤条件校验与构建 ====================

    /**
     * 校验过滤条件并构建 SQL 子句。
     * <p>
     * 对每个 filter 执行：
     * <ol>
     *   <li>字段名是否在 definition 中</li>
     *   <li>op 是否为 IN（v1 不支持）</li>
     *   <li>字段类型是否可筛选</li>
     *   <li>op 是否在该类型的合法集合中</li>
     * </ol>
     * 全部通过后将逻辑字段名转为物理列名，生成 SQL 片段与参数值。
     * </p>
     */
    private List<FilterClause> validateAndBuildClauses(List<FormDataFilter> filters,
                                                        Map<String, FieldType> fieldTypeMap) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }

        List<FilterClause> clauses = new ArrayList<>();

        for (FormDataFilter filter : filters) {
            String field = filter.getField();
            FilterOp op = filter.getOp();
            Object value = filter.getValue();

            // —— 字段名非空 ——
            if (field == null || field.isBlank()) {
                throw new BaseException(FormErrorCode.QUERY_FILTER_FIELD_UNKNOWN, "过滤字段名为空");
            }

            // —— op 非空 ——
            if (op == null) {
                throw new BaseException(FormErrorCode.QUERY_FILTER_OP_NOT_SUPPORTED, "过滤操作符为空");
            }

            // —— v1 不支持 IN ——
            if (op == FilterOp.IN) {
                throw new BaseException(FormErrorCode.QUERY_FILTER_OP_NOT_SUPPORTED,
                        "过滤操作符 IN 在 v1 暂不支持");
            }

            // —— 字段是否在 definition 中 ——
            FieldType fieldType = fieldTypeMap.get(field);
            if (fieldType == null) {
                throw new BaseException(FormErrorCode.QUERY_FILTER_FIELD_UNKNOWN,
                        "过滤字段 '" + field + "' 不在表单定义中");
            }

            // —— 字段类型是否可筛选 ——
            if (NON_FILTERABLE_TYPES.contains(fieldType) || !fieldType.isEnabled()) {
                throw new BaseException(FormErrorCode.QUERY_FILTER_FIELD_NOT_FILTERABLE,
                        "字段 '" + field + "'（类型 " + fieldType + "）不支持筛选");
            }

            // —— op 是否在该类型的合法集合中 ——
            Set<FilterOp> allowed = ALLOWED_OPS.get(fieldType);
            if (allowed == null || !allowed.contains(op)) {
                throw new BaseException(FormErrorCode.QUERY_FILTER_OP_TYPE_MISMATCH,
                        "操作符 " + op + " 不适用于字段 '" + field + "'（类型 " + fieldType + "）");
            }

            // —— 值非空（空值过滤无意义） ——
            if (value == null || (value instanceof String s && s.isBlank())) {
                throw new BaseException(FormErrorCode.QUERY_FILTER_OP_TYPE_MISMATCH,
                        "过滤字段 '" + field + "' 的值为空");
            }

            // —— 物理列名（唯一出口） ——
            String physicalCol = ColumnValidation.physicalColumnName(field, fieldType);

            // —— 构建 SQL 子句 ——
            clauses.add(buildClause(physicalCol, op, value, fieldType));
        }

        return clauses;
    }

    /**
     * 构建单个过滤 SQL 子句与参数值。
     */
    private FilterClause buildClause(String colName, FilterOp op, Object value, FieldType fieldType) {
        String sql;
        Object paramValue;

        switch (op) {
            case EQ -> {
                sql = "\"" + colName + "\" = ?";
                paramValue = convertFilterValue(value, fieldType);
            }
            case LIKE -> {
                sql = "\"" + colName + "\" LIKE ? ESCAPE '\\'";
                paramValue = escapeLike(value.toString());
            }
            case GE -> {
                sql = "\"" + colName + "\" >= ?";
                paramValue = convertFilterValue(value, fieldType);
            }
            case LE -> {
                sql = "\"" + colName + "\" <= ?";
                paramValue = convertFilterValue(value, fieldType);
            }
            default -> throw new BaseException(FormErrorCode.QUERY_FILTER_OP_NOT_SUPPORTED,
                    "不支持的操作符: " + op);
        }

        return new FilterClause(sql, paramValue);
    }

    /**
     * 转换过滤值为数据库可比类型。
     * <p>
     * BOOL：true/false/1/0 → Integer 1/0（对齐 SMALLINT 存储）。
     * 其他类型原样返回，由 JDBC 驱动处理。
     * </p>
     */
    private Object convertFilterValue(Object value, FieldType fieldType) {
        if (fieldType == FieldType.BOOL) {
            if (value instanceof Boolean b) return b ? 1 : 0;
            if (value instanceof Number n) return n.intValue() != 0 ? 1 : 0;
            if (value instanceof String s) {
                return switch (s.trim().toLowerCase()) {
                    case "true", "1", "yes", "on" -> 1;
                    case "false", "0", "no", "off", "" -> 0;
                    default -> throw new BaseException(FormErrorCode.QUERY_FILTER_OP_TYPE_MISMATCH,
                            "无法将 '" + s + "' 转换为布尔值");
                };
            }
            throw new BaseException(FormErrorCode.QUERY_FILTER_OP_TYPE_MISMATCH,
                    "无法将 " + value.getClass().getSimpleName() + " 转换为布尔值");
        }
        return value;
    }

    /**
     * LIKE 值转义：转义 \% \_ \\，并包裹 %value% 做包含匹配。
     * <p>
     * ESCAPE '\' 对齐 PostgreSQL / H2 行为。
     * </p>
     */
    static String escapeLike(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    // ==================== 列投影 ====================

    /**
     * 构建 SELECT 列投影。
     * <p>
     * 列集 = definition 中 type≠TABLE 的字段物理列（过白名单）
     * + REFERENCE 的 ref_{name}_id + 系统列。
     * </p>
     */
    /**
     * 投影列集合：系统列中剔除列表视图不需要的 deleted、tenant_id、version。
     * <p>
     * deleted 恒 0 对用户无意义；tenant_id 恒当前租户（噪音且轻微泄漏）；
     * version 是乐观锁、编辑期关注，列表不关心。
     * 注意：只剔除 SELECT 投影，WHERE 过滤照常保留 deleted=0 AND tenant_id=?。
     * </p>
     */
    private static final List<String> PROJECTION_SYSTEM_COLUMNS = List.of(
            "id", "create_time", "create_by", "update_time", "update_by"
    );

    /**
     * 构建 SELECT 列投影。
     * <p>
     * 列集 = definition 中 type≠TABLE 的字段物理列（过白名单）
     * + REFERENCE 的 ref_{name}_id + 投影系统列。
     * 列表视图排除 deleted、tenant_id、version（无业务意义）。
     * </p>
     */
    private List<String> buildProjection(Map<String, FieldType> fieldTypeMap) {
        List<String> columns = new ArrayList<>(PROJECTION_SYSTEM_COLUMNS);

        for (Map.Entry<String, FieldType> entry : fieldTypeMap.entrySet()) {
            FieldType ft = entry.getValue();

            // 跳过不可投影类型
            if (ft == FieldType.TABLE) continue;
            if (!ft.isEnabled()) continue;

            String physicalCol = ColumnValidation.physicalColumnName(entry.getKey(), ft);
            columns.add(physicalCol);
        }

        return columns;
    }

    // ==================== 分页参数钳制 ====================

    private int clampSize(int size) {
        if (size < 1) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    // ==================== 内部类型 ====================

    /**
     * SQL 过滤子句：SQL 片段 + 参数值。
     */
    private record FilterClause(String sql, Object value) {}
}
