package com.sw.ck.system.api.dict;

import java.util.List;

/**
 * 字典服务 Facade 接口。
 * <p>
 * 定义于 {@code -api} 模块，由 {@code -biz} 模块实现。
 * 其它模块（如 form、workflow）需要消费字典数据时，<strong>仅可</strong>依赖本接口，
 * 禁止直接访问 {@code sys_dict_type} / {@code sys_dict_data} 表或对应的 Mapper。
 * </p>
 */
public interface DictFacade {

    /**
     * 根据字典类型编码查询字典数据项列表。
     *
     * @param dictType 字典类型编码（如 {@code sys_common_status}）
     * @return 字典数据项列表（已按 sort 升序排列，不含停用项）
     */
    List<DictItemDTO> listByType(String dictType);

    /**
     * 校验指定字典类型下是否存在指定的字典值。
     * <p>
     * 用于表单字典控件提交时的值域校验。
     * </p>
     *
     * @param dictType 字典类型编码
     * @param code     字典值（dict_value）
     * @return true 若该值在字典值域内
     */
    boolean isValidCode(String dictType, String code);

    /**
     * 根据字典类型和值解析对应的标签。
     *
     * @param dictType 字典类型编码
     * @param code     字典值（dict_value）
     * @return 字典标签；若未找到返回 {@code null}
     */
    String resolveLabel(String dictType, String code);
}
