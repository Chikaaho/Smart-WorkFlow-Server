package com.sw.ck.bpm.api.spi.assignee;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 节点审批人解析上下文。
 * <p>
 * 【一次定宽，为脚本档留料，禁删减】7 字段固定，DESIGNATED 虽只用 approverValue，
 * 其余字段不得删除，确保 SCRIPT 型实现可顺利接入。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeApproverContext implements Serializable {

    /** 当前租户 ID。 */
    private Long tenantId;

    /** 流程实例 ID（Flowable）。 */
    private String processInstanceId;

    /** 节点标识（BPMN UserTask 元素 ID，对应画布节点 id）。 */
    private String nodeKey;

    /** 业务键（表单动态宽表 recordId）。 */
    private String businessKey;

    /** 绑定表单 formKey。 */
    private String formKey;

    /** 审批人配置值（DESIGNATED 为 userId 数组，SCRIPT 为脚本文本）。 */
    private Object approverValue;

    /** 发起人用户 ID。 */
    private Long initiatorUserId;
}
