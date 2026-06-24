package com.sw.ck.form.entity;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 表单模块 ID 生成器。
 * <p>
 * 生成 UUID v4（36 字符含连字符）作为动态表单及其元数据表的主键。
 * 实现 {@link IdentifierGenerator} 以配合 MyBatis-Plus 的 {@code ASSIGN_UUID} 策略。
 * </p>
 *
 * <p>封装为独立组件，便于将来替换为 UUIDv7 或其他有序 ID 算法时无需改动业务代码。</p>
 */
@Component
public class FormIdGenerator implements IdentifierGenerator {

    /**
     * 生成一个 UUID v4 字符串（36 字符，含连字符）。
     * 格式示例：{@code 550e8400-e29b-41d4-a716-446655440000}
     *
     * @return UUID 字符串，匹配 VARCHAR(36)
     */
    public String generateId() {
        return UUID.randomUUID().toString();
    }

    // ==================== IdentifierGenerator 接口实现 ====================

    @Override
    public Number nextId(Object entity) {
        // FormBaseEntity 体系使用 ASSIGN_UUID，不调用此方法
        throw new UnsupportedOperationException("FormIdGenerator only supports UUID generation; use ASSIGN_UUID strategy");
    }

    @Override
    public String nextUUID(Object entity) {
        return generateId();
    }

    @Override
    public boolean assignId(Object idValue) {
        return true;
    }
}
