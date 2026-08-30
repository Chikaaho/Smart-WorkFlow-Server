package com.sw.ck.form.entity;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 表单业务编号生成器。
 * <p>
 * 生成 UUID v4（36 字符含连字符）作为动态表单及其元数据表的主键。
 * 仅作为普通业务组件注入使用，不再承担全局主键生成职责。
 * </p>
 *
 * <p>封装为独立组件，便于将来替换为 UUIDv7 或其他有序 ID 算法时无需改动业务代码。</p>
 */
@Component
public class FormIdGenerator {

    /**
     * 生成一个 UUID v4 字符串（36 字符，含连字符）。
     * 格式示例：{@code 550e8400-e29b-41d4-a716-446655440000}
     *
     * @return UUID 字符串，匹配 VARCHAR(36)
     */
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
