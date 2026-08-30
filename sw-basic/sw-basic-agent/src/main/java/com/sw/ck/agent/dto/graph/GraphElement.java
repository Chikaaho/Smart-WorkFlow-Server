package com.sw.ck.agent.dto.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 图元素 —— 节点或边（M07-F02 Step7 图定义模型，对齐 sw-bpm {@code GraphElement} 先例）。
 * <p>
 * 节点：kind="node"，type ∈ {START, END, LLM, TOOL, CONDITION, ...}（可扩展），source/target 为 null。<br>
 * 边：kind="edge"，type 为 null，通过 source/target 引用两端节点 id。
 * </p>
 * <p>
 * <b>后端仅解释拓扑（id/kind/type/source/target），{@code config} 与 {@code style} 为不透明
 * Map 原样透传，严禁在后端解析其内部字段。</b>（config 中的条件分支配置等语义由
 * Step8 图解释执行引擎定义与消费。）
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GraphElement implements Serializable {

    /** 元素唯一标识（设计器分配）。 */
    private String id;

    /** 元素种类："node" | "edge"。 */
    private String kind;

    /** 节点类型（START/END/LLM/TOOL/CONDITION/…），边为 null。 */
    private String type;

    /** 边起点节点 id（仅边使用，节点为 null）。 */
    private String source;

    /** 边终点节点 id（仅边使用，节点为 null）。 */
    private String target;

    /** 不透明配置（后端不解释，原样透传）。 */
    private Map<String, Object> config;

    /** 不透明样式（画布样式，原样透传）。 */
    private Map<String, Object> style;
}
