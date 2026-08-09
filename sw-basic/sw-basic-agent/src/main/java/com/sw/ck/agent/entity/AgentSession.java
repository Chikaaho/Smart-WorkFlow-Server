package com.sw.ck.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 会话主表（M07 Step4 F04 对话交互）。
 * <p>
 * 继承 {@link BaseEntity}（含 id/tenantId/createTime/createBy/updateTime/updateBy/deleted/version），
 * id 由 MyBatis-Plus {@code IdType.ASSIGN_ID} 生成雪花 ID，审计字段由
 * {@code CommonMetaObjectHandler} 填充。
 * </p>
 * <p>
 * 生命周期：用户首次调用编排（请求无 sessionId）时自动创建，后续调用携带 sessionId 复用；
 * 状态写死 {@code ACTIVE}（方案 §3 不包含删除/归档/状态流转管理，会话永久有效）；
 * title 自动生成（从首条消息截取）留后续迭代，当前为 null。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_agent_session")
public class AgentSession extends BaseEntity {

    /** 大模型接入配置 id（关联 sw_agent_model_config） */
    private Long agentModelConfigId;

    /** 会话标题（自动生成留后续迭代，当前 null） */
    private String title;

    /** 会话状态：ACTIVE（varchar + String，对齐 sw_bpm_instance 惯例） */
    private String status;
}
