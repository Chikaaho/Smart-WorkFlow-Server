package com.sw.ck.bpm.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 创建流程定义响应 —— 返回 DB 生成的 def id + 初始图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProcessDefResponse implements Serializable {

    /** 流程定义数据库 ID（Snowflake Long）。 */
    private Long defId;

    /** 初始图（START → END，开箱合法）。 */
    private ProcessGraph graph;
}
