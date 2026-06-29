package com.sw.ck.common.datascope;

/**
 * 通用「id + 展示值」只读二元组。
 * <p>
 * 用于 REFERENCE 关联控件候选列表展示、回显等场景，由构造方负责将内部
 * id（UUID/Long…）自行 {@code toString} 为 {@code String} 传入。
 * <p>
 * 注意：本类型仅承载最简键值对，不取代 {@code DictItem}（字典项更厚，带
 * label/tagType 等元数据）。
 *
 * @param id    记录唯一标识（由调用方自行转为字符串）
 * @param value 展示值（label / 显示文本）
 */
public record IdValueProperty(String id, String value) {}
