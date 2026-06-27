package com.sw.ck.workflow.api.spi;

import com.sw.ck.workflow.api.dto.ApproverContext;

/**
 * 审批人解析 SPI。
 * <p>
 * 定义在 <code>sw-biz-workflow-api</code>（CLAUDE.md §1 跨模块通信），
 * 各实现（固定审批人、角色、岗位、主管、上级、字段匹配、自选等）注册于
 * <code>sw-biz-workflow-biz</code>，经 Spring 注入调用。
 * </p>
 *
 * @see com.sw.ck.workflow.api.dto.ApproverContext
 */
public interface ApproverResolver {

    /**
     * 根据发起上下文解析审批人。
     *
     * @param context 流程发起上下文（至少含 formKey、submittedData、submitter、tenantId）
     * @return 审批人用户 ID（字符串形式，对应 Flowable assignee 变量 ${approver}），
     *         不可返回 null
     */
    String resolve(ApproverContext context);
}
