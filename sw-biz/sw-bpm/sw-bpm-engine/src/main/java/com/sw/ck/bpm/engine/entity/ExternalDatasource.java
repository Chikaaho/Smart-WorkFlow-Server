package com.sw.ck.bpm.engine.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 外部数据源连接信息（主库表，继承 BaseEntity 走租户隔离）。
 * <p>
 * password_cipher 字段使用 {@link JsonIgnore} 屏蔽 JSON 序列化，
 * 查询返回、日志、前端回显一律不透出密文。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_bpm_ext_datasource")
public class ExternalDatasource extends BaseEntity {

    /** 数据源名称 */
    @TableField("name")
    private String name;

    /** 数据库类型：mysql, postgresql, oracle, h2 等 */
    @TableField("type")
    private String type;

    /** JDBC 连接 URL */
    @TableField("jdbc_url")
    private String jdbcUrl;

    /** JDBC 驱动类 */
    @TableField("driver_class")
    private String driverClass;

    /** 连接用户名 */
    @TableField("username")
    private String username;

    /** AES-256-GCM 加密密码密文（不入日志、不入响应） */
    @JsonIgnore
    @TableField("password_cipher")
    private String passwordCipher;

    /** 是否只读：1=是，0=否 */
    @TableField("read_only")
    private Integer readOnly;

    /** 是否启用：1=启用，0=禁用 */
    @TableField("enabled")
    private Integer enabled;
}
