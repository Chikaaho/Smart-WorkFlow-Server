package com.sw.ck.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户岗位关联。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_post")
public class SysUserPost extends BaseEntity {
    @TableField("user_id") private Long userId;
    @TableField("post_id") private Long postId;
}
