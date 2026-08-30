package com.sw.ck.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户组-用户 多对多成员关系。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_group_member")
public class SysUserGroupMember extends BaseEntity {
    @TableField("group_id") private Long groupId;
    @TableField("user_id") private Long userId;
}
