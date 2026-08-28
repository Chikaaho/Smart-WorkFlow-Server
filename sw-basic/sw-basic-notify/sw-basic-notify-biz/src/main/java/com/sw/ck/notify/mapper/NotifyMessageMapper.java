package com.sw.ck.notify.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.sw.ck.common.mapper.BaseMapperX;
import com.sw.ck.notify.entity.NotifyMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 站内信通知 Mapper。
 */
@Mapper
public interface NotifyMessageMapper extends BaseMapperX<NotifyMessage> {

    /**
     * 按部门ID列表查询当前租户内有效用户ID（排除停用用户）。
     * <p>
     * 手写 tenant_id 条件，使用 @InterceptorIgnore 跳过租户拦截器
     * （本方法查询 sys_user 表，非 sw_notify_message 表）。
     * </p>
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT DISTINCT u.id FROM sys_user u "
            + "WHERE u.deleted = 0 AND u.status = 0 "
            + "AND u.tenant_id = #{tenantId} "
            + "AND u.dept_id IN "
            + "<foreach collection='deptIds' item='deptId' open='(' separator=',' close=')'>"
            + "#{deptId}"
            + "</foreach>"
            + "</script>")
    List<Long> selectActiveUserIdsByDeptIds(@Param("deptIds") List<Long> deptIds,
                                           @Param("tenantId") Long tenantId);

    /**
     * 校验部门对象属于当前租户、未删除且启用（status=0）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT id FROM sys_dept "
            + "WHERE deleted = 0 AND status = 0 "
            + "AND tenant_id = #{tenantId} AND id IN "
            + "<foreach collection='deptIds' item='deptId' open='(' separator=',' close=')'>"
            + "#{deptId}"
            + "</foreach>"
            + "</script>")
    List<Long> selectValidDeptIds(@Param("deptIds") List<Long> deptIds,
                                  @Param("tenantId") Long tenantId);

    /**
     * 按角色code列表查询当前租户内有效用户ID（排除停用用户）。
     * <p>
     * 手写 tenant_id 条件，使用 @InterceptorIgnore 跳过租户拦截器。
     * </p>
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT DISTINCT ur.user_id FROM sys_user_role ur "
            + "INNER JOIN sys_user u ON u.id = ur.user_id AND u.deleted = 0 AND u.status = 0 "
            + "INNER JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1 "
            + "WHERE ur.deleted = 0 AND ur.tenant_id = #{tenantId} "
            + "AND u.tenant_id = #{tenantId} AND r.tenant_id = #{tenantId} "
            + "AND r.code IN "
            + "<foreach collection='roleCodes' item='code' open='(' separator=',' close=')'>"
            + "#{code}"
            + "</foreach>"
            + "</script>")
    List<Long> selectActiveUserIdsByRoleCodes(@Param("roleCodes") List<String> roleCodes,
                                              @Param("tenantId") Long tenantId);

    /**
     * 校验角色对象属于当前租户、未删除且启用（status=1）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT code FROM sys_role "
            + "WHERE deleted = 0 AND status = 1 "
            + "AND tenant_id = #{tenantId} AND code IN "
            + "<foreach collection='roleCodes' item='code' open='(' separator=',' close=')'>"
            + "#{code}"
            + "</foreach>"
            + "</script>")
    List<String> selectValidRoleCodes(@Param("roleCodes") List<String> roleCodes,
                                      @Param("tenantId") Long tenantId);

    /**
     * 按用户ID列表查询当前租户内的有效用户ID（排除停用/已删除/跨租户用户）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT id FROM sys_user "
            + "WHERE deleted = 0 AND status = 0 "
            + "AND tenant_id = #{tenantId} "
            + "AND id IN "
            + "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>"
            + "#{userId}"
            + "</foreach>"
            + "</script>")
    List<Long> selectValidUserIds(@Param("userIds") List<Long> userIds,
                                 @Param("tenantId") Long tenantId);
}
