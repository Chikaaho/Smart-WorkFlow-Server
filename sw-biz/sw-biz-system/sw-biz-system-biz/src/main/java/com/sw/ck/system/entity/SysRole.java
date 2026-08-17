package com.sw.ck.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 系统角色表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色名称 */
    @TableField("name")
    private String name;

    /** 角色标识（返回前端的 role key） */
    @TableField("code")
    private String code;

    /** 排序 */
    @TableField("sort")
    private Integer sort;

    /** 状态：1=启用 0=停用 */
    @TableField("status")
    private Integer status;

    /** 数据范围（对齐 sw-security holder/DataScope 枚举 ordinal：0=ALL 1=DEPT 2=DEPT_AND_CHILD 3=SELF 4=CUSTOM） */
    @TableField("data_scope")
    private Integer dataScope;

    /**
     * 角色关联部门 ID 集合（sys_role_dept，仅 CUSTOM 数据范围下有意义）。
     * 请求侧：新建/更新时写入；响应侧：详情/列表回填供前端回显。非表列，不参与持久化。
     */
    @TableField(exist = false)
    private List<Long> deptIds;

    /** 内置标记 */
    @TableField("built_in")
    private Boolean builtIn;

    /** 备注/描述 */
    @TableField("remark")
    private String description;
}
