package com.sw.ck.lowcode.api.form;

/**
 * 表单定义查询服务 SPI。
 * <p>
 * 工作流模块通过此接口获取表单定义，不直接依赖 lowcode-biz。
 * 实现由 sw-biz-lowcode-biz 提供。
 */
public interface FormDefinitionService {

    /**
     * 根据 formKey 获取表单定义 JSON。
     *
     * @param formKey 表单标识
     * @return 表单定义的 JSON 字符串，不存在时返回 null
     */
    String getFormDefinition(String formKey);

    /**
     * 判断表单定义是否存在。
     *
     * @param formKey 表单标识
     * @return true 如果存在
     */
    boolean formExists(String formKey);
}
