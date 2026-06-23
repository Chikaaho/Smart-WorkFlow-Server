package com.sw.ck.security.spi;

import com.sw.ck.security.holder.LoginUser;

/**
 * 由 sw-module-system 实现：security 框架本身不持有用户数据，仅定义加载契约。
 * <p>
 * 契约未变更方法签名（仍只接收 username/userId、返回 {@link LoginUser}）：Prompt 2 给
 * {@link LoginUser} 新增的 tenantId/deptId/dataScope/customDeptIds/superAdmin 字段已经是
 * 返回类型自身的一部分，实现方只需在构造返回值时一次性把这些字段查全、填满即可，无需
 * 改变方法签名或新增方法。
 * <p>
 * 调用方（认证流程，Prompt 3）应优先从 {@code LoginUserCacheService} 读取缓存的完整
 * LoginUser，缓存未命中时才调用本接口回查，并将结果写回缓存——JWT 本身只携带 userId，
 * 不下放 deptId/dataScope/permissions 等会变化的权限信息，以便权限变更后只需让缓存失效
 * 即可立即生效，无需等 token 过期。
 */
public interface UserDetailsProvider {

    LoginUser loadByUsername(String username);

    LoginUser loadByUserId(Long userId);
}
