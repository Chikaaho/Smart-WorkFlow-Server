package com.sw.ck.bpm.process.resolver;

import com.sw.ck.bpm.api.dto.ApproverContext;
import com.sw.ck.bpm.api.spi.ApproverResolver;
import org.springframework.stereotype.Component;

/**
 * 固定审批人解析器（骨架实现）。
 * <p>
 * 返回发起人作为默认审批人。后续可替换为可配置的审批人 ID（配置文件 / 数据库）。
 * 其余审批人策略（角色 / 岗位 / 主管 / 上级 / 字段 / 自选）为后续独立实现，
 * ❌ 禁止在此类中硬编码 if/else 分支。
 * </p>
 */
@Component
public class FixedApproverResolver implements ApproverResolver {

    /**
     * 骨架阶段：始终返回发起人（submitter）的字符串表示。
     *
     * @param context 流程发起上下文
     * @return submitter 的字符串形式，作为 Flowable assignee
     */
    @Override
    public String resolve(ApproverContext context) {
        if (context == null || context.getSubmitter() == null) {
            throw new IllegalArgumentException("ApproverContext 或 submitter 不能为空");
        }
        return String.valueOf(context.getSubmitter());
    }
}
