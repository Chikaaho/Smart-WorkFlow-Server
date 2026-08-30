package com.sw.ck.common.config.jackson;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 全局 Jackson Long/long → String 序列化配置。
 *
 * <p>根因：雪花 ID 是 64 位 long（19 位），JS Number 安全整数上限 2^53-1（16 位）。
 * 后端把 long 当 JSON 数字发出，前端 JSON.parse 落进 Number 即截断精度（实测
 * 2071161019255910402 → 2071161019255910400），导致编辑/删除按错 id 查无此行或静默命中相邻行。
 *
 * <p>方案：全局注册 {@link ToStringSerializer}，仅作用于 {@link Long}/{@code long} 类型，
 * 使得所有 Long 类型字段（主要为雪花 ID）序列化为 JSON string，彻底根治前端精度截断。
 * {@link Integer}/{@code int} 不受影响，仍为 JSON 数字。
 *
 * <p>反序列化：Jackson 原生支持 String → Long，无需额外配置。
 *
 * <p>before = JacksonAutoConfiguration.class：确保在 Jackson 自动装配 ObjectMapper
 * 之前注册本 Module，避免 ObjectMapper 已创建后再追加。
 */
@AutoConfiguration(before = JacksonAutoConfiguration.class)
public class JacksonLongToStringConfig {

    @Bean
    public Module longToStringModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }
}
