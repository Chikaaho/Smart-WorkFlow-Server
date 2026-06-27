package com.sw.ck.system.config;

import com.sw.ck.security.spi.UserDetailsProvider;
import com.sw.ck.system.mapper.SysMenuMapper;
import com.sw.ck.system.mapper.SysRoleMapper;
import com.sw.ck.system.mapper.SysRoleMenuMapper;
import com.sw.ck.system.mapper.SysUserRoleMapper;
import com.sw.ck.system.security.UserDetailsProviderImpl;
import com.sw.ck.system.service.SysUserService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 系统管理模块自动配置。
 * <p>
 * 注册 {@link UserDetailsProvider} 实现（激活 sw-security 的 LoginUserLoader 与 JWT 认证流程）
 * 以及 {@link PasswordEncoder}（sw-security 本身不提供，在此补齐）。
 * </p>
 */
@AutoConfiguration
public class SystemAutoConfiguration {

    /**
     * 注册 UserDetailsProvider 实现，触发 sw-security 的 LoginUserCacheService / LoginUserLoader
     * 自动装载（参见 {@link com.sw.ck.security.config.SecurityAutoConfiguration}）。
     * <p>
     * 注入 RBAC Mapper 用于组装 roles / permissions / superAdmin（替换旧有 userId==1 硬编）。
     * </p>
     */
    @Bean
    public UserDetailsProvider userDetailsProvider(SysUserService sysUserService,
                                                   SysUserRoleMapper sysUserRoleMapper,
                                                   SysRoleMapper sysRoleMapper,
                                                   SysRoleMenuMapper sysRoleMenuMapper,
                                                   SysMenuMapper sysMenuMapper) {
        return new UserDetailsProviderImpl(sysUserService, sysUserRoleMapper, sysRoleMapper,
                sysRoleMenuMapper, sysMenuMapper);
    }

    /**
     * BCrypt 密码编码器（sw-security 不提供，业务方负责注册）。
     * 使用 strength=10，与其他模块一致。
     */
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder bcryptPasswordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
