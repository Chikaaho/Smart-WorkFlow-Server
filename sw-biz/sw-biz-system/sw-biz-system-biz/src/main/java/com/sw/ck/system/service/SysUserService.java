package com.sw.ck.system.service;

import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseService;
import com.sw.ck.system.entity.SysUser;
import java.util.List;

/**
 * 系统用户 Service。
 */
public interface SysUserService extends BaseService<SysUser> {

    /**
     * 创建用户（密码 BCrypt 编码后入库）。
     *
     * @param user       用户实体
     * @param plainPassword 明文密码
     */
    Long create(SysUser user, String plainPassword);

    /** 创建用户及其岗位、角色关系，必须在同一事务内完成。 */
    Long createWithAssociations(SysUser user, String plainPassword, List<Long> roleIds, List<Long> postIds);

    /**
     * 更新用户（若提供新密码则重新编码）。
     *
     * @param user          用户实体
     * @param plainPassword 新明文密码（null 表示不修改密码）
     */
    void update(SysUser user, String plainPassword);

    /** 更新用户及其岗位、角色关系，必须在同一事务内完成。 */
    void updateWithAssociations(SysUser user, String plainPassword, List<Long> roleIds, List<Long> postIds);

    /**
     * 删除用户（逻辑删除）。
     */
    void delete(Long id);

    /**
     * 分页查询用户。
     */
    PageResult<SysUser> page(PageParam pageParam);
    PageResult<SysUser> page(PageParam pageParam, UserPageQuery query);

    /**
     * 根据用户名查询用户。
     */
    SysUser getByUsername(String username);

    /**
     * 根据 ID 查询用户。
     */
    SysUser getById(Long id);

    List<Long> listRoleIds(Long userId);

    void updateRoleIds(Long userId, List<Long> roleIds);

    List<Long> listPostIds(Long userId);

    void updatePostIds(Long userId, List<Long> postIds);
}
