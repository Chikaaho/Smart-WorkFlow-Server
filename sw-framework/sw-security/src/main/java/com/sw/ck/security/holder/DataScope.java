package com.sw.ck.security.holder;

/**
 * 数据范围编码，由 {@link com.sw.ck.security.spi.UserDetailsProvider} 回查填充到 {@link LoginUser}。
 * 具体的拦截/过滤逻辑留给 Prompt 4，本枚举只承载编码本身。
 */
public enum DataScope {

    /** 全部数据。 */
    ALL,
    /** 仅本部门。 */
    DEPT,
    /** 本部门及子部门。 */
    DEPT_AND_CHILD,
    /** 仅本人。 */
    SELF,
    /** 自定义部门集合，见 {@link LoginUser#getCustomDeptIds()}。 */
    CUSTOM
}
