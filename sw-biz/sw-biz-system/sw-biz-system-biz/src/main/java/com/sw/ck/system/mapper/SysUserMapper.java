package com.sw.ck.system.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sw.ck.common.datascope.DataScope;
import com.sw.ck.common.mapper.BaseMapperX;
import com.sw.ck.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 系统用户 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapperX<SysUser> {

    /**
     * 用户分页查询（数据范围纳管入口）。
     * <p>
     * {@code @DataScope}（deptAlias/userAlias 均为空 = 单表无别名）由
     * {@code DataScopeHandler} 按当前登录人数据范围拼接条件：sys_user 同时具备
     * dept_id（部门三档）与 create_by（SELF 档）两列，五档全部由 handler 处理。
     * 逻辑删除条件手动拼接（自定义 @Select 不经过 MP 实体逻辑删除模板）。
     * </p>
     */
    @DataScope
    @Select("SELECT * FROM sys_user WHERE deleted = 0")
    IPage<SysUser> selectUserPage(Page<SysUser> page);
}
