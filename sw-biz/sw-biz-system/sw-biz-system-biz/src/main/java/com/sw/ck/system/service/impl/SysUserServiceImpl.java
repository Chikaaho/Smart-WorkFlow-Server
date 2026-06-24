package com.sw.ck.system.service.impl;

import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.system.entity.SysUser;
import com.sw.ck.system.mapper.SysUserMapper;
import com.sw.ck.system.service.SysUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 系统用户 Service 实现。
 */
@Service
public class SysUserServiceImpl
        extends BaseServiceImpl<SysUserMapper, SysUser>
        implements SysUserService {

    private final PasswordEncoder passwordEncoder;

    public SysUserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
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
        return baseMapper.selectPage(pageParam, null);
    }

    @Override
    public SysUser getByUsername(String username) {
        return lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    @Override
    public SysUser getById(Long id) {
        return baseMapper.selectById(id);
    }
}
