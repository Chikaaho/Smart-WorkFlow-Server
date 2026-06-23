package com.sw.ck.common.config.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.sw.ck.common.constant.CommonConstants;
import com.sw.ck.common.security.LoginContextProvider;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

public class CommonMetaObjectHandler implements MetaObjectHandler {

    private final LoginContextProvider loginContextProvider;

    public CommonMetaObjectHandler(LoginContextProvider loginContextProvider) {
        this.loginContextProvider = loginContextProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
        strictInsertFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        strictInsertFill(metaObject, "createBy", this::currentUserId, Long.class);
        strictInsertFill(metaObject, "updateBy", this::currentUserId, Long.class);
        strictInsertFill(metaObject, "tenantId", this::currentTenantId, Long.class);
        strictInsertFill(metaObject, "deleted", () -> 0, Integer.class);
        strictInsertFill(metaObject, "version", () -> 0L, Long.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        strictUpdateFill(metaObject, "updateBy", this::currentUserId, Long.class);
    }

    /**
     * 取不到登录态（系统初始化、定时任务等无登录上下文场景）时，降级为系统操作人 {@link CommonConstants#SYSTEM_OPERATOR_ID}。
     */
    private Long currentUserId() {
        Long userId = loginContextProvider.getUserId();
        return userId != null ? userId : CommonConstants.SYSTEM_OPERATOR_ID;
    }

    /**
     * 取不到登录态时，降级为超级租户 {@link CommonConstants#SUPER_TENANT_ID}。
     */
    private Long currentTenantId() {
        Long tenantId = loginContextProvider.getTenantId();
        return tenantId != null ? tenantId : Long.valueOf(CommonConstants.SUPER_TENANT_ID);
    }
}
