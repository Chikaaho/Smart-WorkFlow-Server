package com.sw.ck.common.config.mybatis.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 列级多租户配置。
 */
@Data
@ConfigurationProperties(prefix = "sw.tenant")
public class TenantProperties {

    /**
     * 是否装载租户拦截器；单租户部署设为 false 可整体关闭。
     */
    private boolean enabled = true;

    /**
     * 无租户概念的全局表白名单（如 sys_tenant 自身），租户拦截器不为这些表追加 tenant_id 条件。
     */
    private List<String> ignoreTables = new ArrayList<>();
}
