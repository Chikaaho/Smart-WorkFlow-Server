package com.sw.ck.common.datascope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在 Mapper 方法上，声明该查询需要按当前登录人的数据范围自动拼接 where 条件。
 * 未标注的方法不受影响。具体如何拼接见
 * {@code com.sw.ck.common.config.mybatis.datascope.DataScopeHandler}。
 * <p>
 * 只对方法所在 Mapper 接口里直接声明（含接口默认方法）的方法生效；继承自
 * {@code BaseMapper} 的通用方法（如 {@code selectList}）不会被反射命中，无法直接标注。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * dept_id 所在表在 SQL 中的别名。单表查询留空（默认），即直接匹配不带别名的表；
     * 多表关联查询时填写该表在 SQL 里的别名，仅会对该别名对应的表拼接条件，不会对联表
     * 中的其它表重复拼接。
     */
    String deptAlias() default "";

    /**
     * create_by 所在表在 SQL 中的别名，含义同 {@link #deptAlias()}，仅 SELF 范围下使用。
     */
    String userAlias() default "";
}
