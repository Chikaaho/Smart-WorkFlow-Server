package com.sw.ck.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.system.entity.SysUser;
import com.sw.ck.system.mapper.SysUserMapper;
import com.sw.ck.system.entity.SysUserRole;
import com.sw.ck.system.mapper.SysUserRoleMapper;
import com.sw.ck.system.service.SysUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.List;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

/**
 * 系统用户 Service 实现。
 */
@Service
public class SysUserServiceImpl
        extends BaseServiceImpl<SysUserMapper, SysUser>
        implements SysUserService {

    private final PasswordEncoder passwordEncoder;
    private final SysUserRoleMapper sysUserRoleMapper;

    public SysUserServiceImpl(PasswordEncoder passwordEncoder) {
        this(passwordEncoder, null);
    }

    @Autowired
    public SysUserServiceImpl(PasswordEncoder passwordEncoder, SysUserRoleMapper sysUserRoleMapper) {
        this.passwordEncoder = passwordEncoder;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysUser user, String plainPassword) {
        Objects.requireNonNull(plainPassword, "密码不能为空");
        user.setPassword(passwordEncoder.encode(plainPassword));
        save(user);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysUser user, String plainPassword) {
        if (plainPassword != null && !plainPassword.isEmpty()) {
            user.setPassword(passwordEncoder.encode(plainPassword));
        } else {
            // 不修改密码：从 DB 加载旧密码保留
            SysUser existing = getById(user.getId());
            if (existing != null) {
                user.setPassword(existing.getPassword());
            }
        }
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        removeById(id);
    }

    @Override
    public PageResult<SysUser> page(PageParam pageParam) {
        // 数据范围条件由 @DataScope 标注的 selectUserPage 经 DataScopeHandler 自动拼接
        return PageResult.of(baseMapper.selectUserPage(
                new Page<>(pageParam.getPageNum(), pageParam.getPageSize())));
    }

    @Override
    public SysUser getByUsername(String username) {
        return lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    @Override
    public SysUser getById(Long id) {
        return baseMapper.selectById(id);
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        return sysUserRoleMapper.selectList(Wrappers.lambdaQuery(SysUserRole.class)
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).filter(Objects::nonNull).distinct().toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRoleIds(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.delete(Wrappers.lambdaQuery(SysUserRole.class).eq(SysUserRole::getUserId, userId));
        if (roleIds == null) return;
        roleIds.stream().filter(Objects::nonNull).distinct().forEach(roleId -> {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            sysUserRoleMapper.insert(relation);
        });
    }
}
