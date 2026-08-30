package com.sw.ck.system.service;

import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseService;
import com.sw.ck.system.entity.SysUser;
import com.sw.ck.system.entity.SysUserGroup;

import java.util.List;

/**
 * 用户组 Service。
 * <p>
 * 语义（D112）：用户组为租户内扁平虚拟集合，不是角色/菜单/按钮/数据权限主体；
 * 成员关系与主记录必须事务一致；只允许绑定同租户、未逻辑删除且当前操作者数据范围内可见的用户。
 * </p>
 */
public interface SysUserGroupService extends BaseService<SysUserGroup> {

    /** 分页查询用户组（数据范围纳管；名称/标识/状态筛选）。 */
    PageResult<SysUserGroup> page(PageParam pageParam, SysUserGroup query);

    /** 用户组详情（含成员回填）。 */
    SysUserGroup getDetail(Long id);

    /** 创建用户组（groupCode 租户内唯一校验）。 */
    Long create(SysUserGroup group);

    /** 更新用户组（groupCode 不可变；唯一性校验排除自身）。 */
    void update(SysUserGroup group);

    /** 停用（不删除配置与成员；停用组不得作为新引用对象）。 */
    void disable(Long id);

    /** 启用。 */
    void enable(Long id);

    /** 逻辑删除用户组（连同成员关系）。 */
    void delete(Long id);

    /** 组成员用户 ID 列表（回填/详情用）。 */
    List<Long> listMemberIds(Long groupId);

    /** 整量替换成员（先校验全部目标用户可见可用，失败整体回滚；空列表=清空）。 */
    void updateMemberIds(Long groupId, List<Long> userIds);

    /** 追加成员（去重；全部校验通过才写入，失败整体回滚）。 */
    void addMemberIds(Long groupId, List<Long> userIds);

    /** 移除指定成员（幂等）。 */
    void removeMemberIds(Long groupId, List<Long> userIds);

    /** 清空成员。 */
    void clearMembers(Long groupId);

    /** 成员候选用户分页（数据范围纳管；仅启用用户）。 */
    PageResult<SysUser> memberCandidates(PageParam pageParam, String keyword);
}
