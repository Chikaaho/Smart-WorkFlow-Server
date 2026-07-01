package com.sw.ck.bpm.api.facade;

import java.util.Map;

/**
 * BPM 运行时门面 —— 封装流程引擎 RuntimeService。
 * <p>
 * 定义流程启动、变量查询等运行时操作契约。
 * 实现类位于 sw-bpm-engine（闭源），由 Spring 注入。
 * </p>
 *
 * @since 1.0.0
 */
public interface BpmRuntimeFacade {

    /**
     * 启动流程实例。
     *
     * @param processDefKey 流程定义 key
     * @param businessKey   业务键（如表单 recordId）
     * @param variables     流程变量
     * @param tenantId      租户 ID
     * @return 流程实例 ID
     */
    String startProcess(String processDefKey, String businessKey,
                        Map<String, Object> variables, String tenantId);
}
