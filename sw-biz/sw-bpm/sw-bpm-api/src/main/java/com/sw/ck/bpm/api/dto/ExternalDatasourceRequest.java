package com.sw.ck.bpm.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 外部数据源创建/更新请求。
 */
@Data
public class ExternalDatasourceRequest {

    @NotBlank(message = "name 不能为空")
    private String name;

    @NotBlank(message = "type 不能为空")
    private String type;

    @NotBlank(message = "jdbcUrl 不能为空")
    private String jdbcUrl;

    @NotBlank(message = "driverClass 不能为空")
    private String driverClass;

    @NotBlank(message = "username 不能为空")
    private String username;

    /** 明文密码（创建时必填，更新时选填） */
    private String password;

    /** 是否只读，默认 1 */
    private Integer readOnly = 1;

    /** 是否启用，默认 1 */
    private Integer enabled = 1;
}
