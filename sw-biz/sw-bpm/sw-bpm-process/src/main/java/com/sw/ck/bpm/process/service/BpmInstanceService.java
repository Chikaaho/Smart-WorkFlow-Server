package com.sw.ck.bpm.process.service;

import com.sw.ck.bpm.process.entity.BpmInstance;
import com.sw.ck.common.service.BaseService;

import java.util.Optional;

/**
 * 流程实例记录 Service。
 *
 * <h3>状态变更</h3>
 * <ul>
 *   <li>{@link #updateStatus(String, String)} 仅更新 status 字段，不触及其他列。
 *       MyBatis-Plus 拦截器自动注入 {@code update_time / update_by}。</li>
 *   <li>调用方负责保证 status 值合法（见 {@link com.sw.ck.bpm.process.entity.InstanceStatusEnum}）。</li>
 * </ul>
 */
public interface BpmInstanceService extends BaseService<BpmInstance> {

    /**
     * 根据 Flowable 实例 ID 查询我方记录。
     *
     * @param processInstanceId Flowable 流程实例 ID
     * @return 流程实例记录（可能为空）
     */
    Optional<BpmInstance> findByProcessInstanceId(String processInstanceId);

    /**
     * 根据业务键（表单 recordId）查询我方记录。
     *
     * @param businessKey 业务键
     * @return 流程实例记录（可能为空）
     */
    Optional<BpmInstance> findByBusinessKey(String businessKey);

    /**
     * 更新流程实例状态。
     * <p>
     * 使用 MyBatis-Plus {@code lambdaUpdate()} 按 processInstanceId 精确匹配更新 status 列，
     * CommonMetaObjectHandler 自动注入 {@code update_time / update_by}。
     * </p>
     *
     * @param processInstanceId Flowable 流程实例 ID，不可为空
     * @param status            目标状态（{@link com.sw.ck.bpm.process.entity.InstanceStatusEnum#getCode()}）
     */
    void updateStatus(String processInstanceId, String status);
}
