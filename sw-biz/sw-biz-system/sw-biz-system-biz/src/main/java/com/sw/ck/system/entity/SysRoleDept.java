package com.sw.ck.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色部门关联表（数据范围 CUSTOM 的可见部门集合）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_dept")
public class SysRoleDept extends BaseEntity {

    /** 角色 ID */
    @TableField("role_id")
    private Long roleId;

    /** 部门 ID */
    @TableField("dept_id")
    private Long deptId;
}
