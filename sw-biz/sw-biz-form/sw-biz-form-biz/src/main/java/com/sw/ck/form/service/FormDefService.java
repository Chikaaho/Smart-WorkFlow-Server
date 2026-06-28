package com.sw.ck.form.service;

import com.sw.ck.form.api.dto.FormDefDTO;
import com.sw.ck.form.entity.FormDefEntity;

/**
 * 表单定义管理服务。
 */
public interface FormDefService {

    /**
     * 创建表单草稿。
     * 只写 sw_form_def + sw_form_config，不碰物理表。
     *
     * @param formKey         表单业务标识
     * @param name            表单名称
     * @param logicalTableName 用户自定义逻辑表名
     * @param description     表单描述
     * @return 创建的草稿 DTO
     */
    FormDefDTO createDraft(String formKey, String name, String logicalTableName, String description);

    /**
     * 更新表单草稿元数据。
     *
     * @param id         表单 ID
     * @param name       表单名称
     * @param logicalTableName 逻辑表名
     * @param description 描述
     * @return 更新后的 DTO
     */
    FormDefDTO updateDraft(String id, String name, String logicalTableName, String description);

    /**
     * 保存表单配置（definition JSON）。
     *
     * @param formId     表单 ID
     * @param definition 表单配置 JSON
     */
    void saveConfig(String formId, String definition);

    /**
     * 发布表单草稿。
     * <p>
     * 字段定义从该表单已存的 {@code sw_form_config.definition.fields} 派生建表，
     * 不再接受外部 fieldSpecs 入参（definition 是唯一字段真源）。
     * </p>
     * <p>
     * 事务边界：
     * <ol>
     *   <li>加载 config.definition 并解析校验字段</li>
     *   <li>校验：逻辑表名 + 所有字段名列名过白名单</li>
     *   <li>创建动态宽表（DynamicTableManager.createFormTable）</li>
     *   <li>回填 physical_table_name / table_name / parent_table → status=PUBLISHED</li>
     *   <li>存一版 definition 到 sw_form_snapshot</li>
     * </ol>
     * DDL 在多数数据库不可回滚，因此校验先行，建表成功后改元数据，
     * 避免半成品状态。
     * </p>
     *
     * @param formId 表单 ID
     */
    void publish(String formId);

    /**
     * 根据 ID 获取表单定义 DTO。
     */
    FormDefDTO getFormDef(String id);

    /**
     * 根据 formKey 获取表单定义 DTO。
     */
    FormDefDTO getFormDefByKey(String formKey);

    /**
     * 根据 formKey 获取表单定义 JSON（渲染接口）。
     *
     * @param formKey 表单业务标识
     * @return definition JSON
     */
    String getDefinition(String formKey);

    /**
     * 根据 ID 获取表单定义 JSON（渲染接口）。
     *
     * @param formId 表单 ID
     * @return definition JSON
     */
    String getDefinitionById(String formId);

    /**
     * 根据 ID 获取表单定义实体。
     */
    FormDefEntity getById(String id);
}
