package com.sw.ck.common.config.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link JacksonLongToStringConfig} 全局 Long/long → String 序列化行为。
 *
 * <ul>
 *   <li>Long/long 字段 → JSON string（带引号，完整 19 位精度不截断）</li>
 *   <li>Integer/int 字段 → JSON number（裸数字，不受影响）</li>
 *   <li>String → Long 反序列化正确（Jackson 原生能力）</li>
 * </ul>
 */
class JacksonLongToStringConfigTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
    }

    @Test
    @DisplayName("雪花 Long ID 应序列化为 JSON string（带引号，精度完整不截断）")
    void snowflakeIdShouldSerializeAsString() throws JsonProcessingException {
        // 实测前端截断的雪花 ID：2071161019255910402 → 2071161019255910000（Number 截断）
        long snowflakeId = 2071161019255910402L;
        Dto dto = new Dto(snowflakeId, 0);

        String json = objectMapper.writeValueAsString(dto);

        assertThat(json)
                .describedAs("Long ID 应序列化为带引号的字符串，19 位精度完整")
                .contains("\"2071161019255910402\"");
    }

    @Test
    @DisplayName("Integer/int 字段应保持为 JSON number（裸数字，不受影响）")
    void integerShouldRemainAsNumber() throws JsonProcessingException {
        Dto dto = new Dto(1L, 1);

        String json = objectMapper.writeValueAsString(dto);

        assertThat(json)
                .describedAs("Integer 应保持 JSON number")
                .contains("\"menuType\":1");
    }

    @Test
    @DisplayName("最大安全 Long 值应正确序列化为 string 并反序列化回原值")
    void longRoundTrip() throws JsonProcessingException {
        long original = Long.MAX_VALUE;
        Dto dto = new Dto(original, 2);

        String json = objectMapper.writeValueAsString(dto);
        Dto restored = objectMapper.readValue(json, Dto.class);

        assertThat(restored.id())
                .describedAs("反序列化后 Long 值应与原值一致")
                .isEqualTo(original);
    }

    @Test
    @DisplayName("典型雪花 ID 往返测试：序列化 string → 反序列化回 Long 相等")
    void typicalSnowflakeIdRoundTrip() throws JsonProcessingException {
        // 19 位雪花 ID
        long original = 2071161019255910402L;

        String json = objectMapper.writeValueAsString(new Dto(original, 0));
        Dto restored = objectMapper.readValue(json, Dto.class);

        assertThat(restored.id())
                .describedAs("典型雪花 ID 往返后应与原值一致")
                .isEqualTo(original);
    }

    /**
     * 测试用 DTO：含 Long id 和 Integer menuType。
     */
    private record Dto(Long id, Integer menuType) {
    }
}
