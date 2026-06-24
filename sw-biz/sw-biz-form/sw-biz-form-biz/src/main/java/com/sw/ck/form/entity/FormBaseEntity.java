package com.sw.ck.form.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单模块基础实体 — UUID 主键体系。
 * <p>
 * 与 {@link com.sw.ck.common.entity.BaseEntity}（雪花 BIGINT 主键）分离，
 * 表单元数据表及动态宽表均使用 UUID（VARCHAR(36)）作主键。
 * </p>
 *
 * <h3>与 BaseEntity 的差异</h3>
 * <ul>
 *   <li>主键类型：{@code String (UUID)} vs {@code Long (Snowflake)}</li>
 *   <li>主键策略：{@link IdType#ASSIGN_UUID} vs {@link IdType#ASSIGN_ID}</li>
 *   <li>审计字段名对齐、类型一致（{@code create_by/update_by} 仍为 BIGINT 指向系统用户表）</li>
 * </ul>
 *
 * <p>审计字段由 {@link com.sw.ck.common.config.mybatis.CommonMetaObjectHandler} 自动填充。</p>
 */
@Data
public abstract class FormBaseEntity implements Serializable {

    /** UUID 主键（VARCHAR(36)），由 {@link FormIdGenerator} 生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 租户 ID（指向系统用户表的 BIGINT 雪花主键，与 BaseEntity 对齐） */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 逻辑删除标志（0=正常，1=已删除） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 创建人（BIGINT，指向 sys_user 雪花主键，与 BaseEntity 对齐） */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 更新人（BIGINT，指向 sys_user 雪花主键） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 乐观锁版本号 */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Long version;
}
