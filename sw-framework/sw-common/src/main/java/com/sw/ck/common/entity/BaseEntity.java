package com.sw.ck.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通用业务实体基类：继承 {@link BaseEntityNoTenant} 并追加 {@code tenantId} 列。
 * <p>
 * 租户级实体（如表 {@code sys_role} / {@code sys_user_role}）应继承此类；
 * 全局表（如 {@code sys_menu}）应直接继承 {@link BaseEntityNoTenant}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BaseEntity extends BaseEntityNoTenant {

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
}
