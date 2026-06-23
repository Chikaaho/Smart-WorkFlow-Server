package com.sw.ck.common.datascope;

/**
 * 数据范围编码。与 sw-security 的 {@code com.sw.ck.security.holder.DataScope} 枚举常量名一一对应，
 * 两者通过 {@link com.sw.ck.common.security.LoginContextProvider#getDataScopeType()} 按
 * {@code name()} 字符串映射解耦——sw-common 不能反向依赖 sw-security，不能直接引用其枚举类型。
 */
public enum DataScopeType {

    /** 全部数据，不拼接任何条件。 */
    ALL,
    /** 仅本部门。 */
    DEPT,
    /** 本部门及下级部门。 */
    DEPT_AND_CHILD,
    /** 仅本人。 */
    SELF,
    /** 自定义部门集合。 */
    CUSTOM
}
