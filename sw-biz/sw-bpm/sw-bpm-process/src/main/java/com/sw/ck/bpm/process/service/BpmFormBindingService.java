package com.sw.ck.bpm.process.service;

import com.sw.ck.bpm.process.entity.BpmFormBinding;
import com.sw.ck.common.service.BaseService;

import java.util.List;

/**
 * 表单↔流程绑定 Service。
 */
public interface BpmFormBindingService extends BaseService<BpmFormBinding> {

    /**
     * 查询当前租户下指定表单的启用绑定。
     *
     * @param formKey 表单业务标识
     * @return 启用绑定列表（理论上最多一条，但遵循骨架原则不做强制）
     */
    List<BpmFormBinding> findActiveByFormKey(String formKey);
}
