package com.sw.ck.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 无租户基类：仅 id + 审计字段 + 逻辑删除 + 乐观锁。
 * <p>
 * 适用于全局表（如 {@code sys_menu}）——不需要 tenant_id 列，
 * 且继承该基类的表必须显式排除出 {@code TenantLineHandler}。
 * <p>
 * {@link BaseEntity} 继承此类并追加 {@code tenantId}，保持现有实体零改动。
 */
@Data
public abstract class BaseEntityNoTenant implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    /**
     * 乐观锁版本号；插入时填充初始值 0，避免首次更新时 version 为 null 导致条件不匹配。
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Long version;
}
