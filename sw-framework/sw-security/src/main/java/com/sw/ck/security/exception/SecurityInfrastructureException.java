package com.sw.ck.security.exception;

/**
 * 安全链「装配 / 基础设施」级故障，与「令牌内容非法」(401) 严格区分。
 * <p>
 * 典型场景：未发现任何 {@code UserDetailsProvider} 实现导致登录用户无法装载（安全链
 * 未正确装配），或回查依赖（Redis / 数据源）不可用。
 * <p>
 * 此类异常【绝不能】被认证过滤器降级为 401 静默放过（参见 system.md §8：令牌失败=401 vs
 * 基础设施/装配故障=500/503，不得统一降级）。它必须向上抛出、由容器渲染为 5xx，使
 * 「安全链不可用」显式暴露，而不是对所有受保护请求静默拒绝、把装配缺陷伪装成「未认证」。
 */
public class SecurityInfrastructureException extends RuntimeException {

    public SecurityInfrastructureException(String message) {
        super(message);
    }
}
