package com.sw.ck.system.mapper;

import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.sw.ck.common.datascope.DataScope;
import com.sw.ck.common.mapper.BaseMapperX;
import com.sw.ck.system.entity.SysDept;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 测试专用受限范围 Mapper（仅测试 classpath 存在，不参与生产装配）。
 * <p>
 * 用途：白盒证明部门查询通道（MyBatis-Plus Mapper / lambdaQuery）对
 * {@link DataPermissionInterceptor} 完全可见——本方法带 {@code @DataScope}
 * 标注（userAlias 为空 = 单表无别名），SELF 档会按当前登录人拼接
 * {@code create_by = userId}。生产部门树查询（SysDeptMapper）无 @DataScope 标注，
 * 其可见边界为租户 + 逻辑删除；本 Mapper 用于证明：若某查询方法声明了可见范围，
 * 走同一通道的祖先补全同样会被拦截，不存在绕过拦截器的裸 SQL 旁路。
 * 注意：DEPT 档会拼接 {@code dept_id} 列，而 sys_dept 无该列，故本 Mapper 仅供
 * SELF/ALL 档演示，不得用于 DEPT 档。
 * </p>
 */
public interface SysDeptScopedMapper extends BaseMapperX<SysDept> {

    @DataScope
    @Select("SELECT * FROM sys_dept WHERE deleted = 0")
    List<SysDept> selectScoped();
}
