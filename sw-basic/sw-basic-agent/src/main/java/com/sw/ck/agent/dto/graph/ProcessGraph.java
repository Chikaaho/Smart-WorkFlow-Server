package com.sw.ck.agent.dto.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Agent 调度图定义模型 —— 图设计器核心数据结构（M07-F02 Step7）。
 * <p>
 * 对应 {@code sw_agent_graph_def.graph_json} 列的序列化格式（对齐 sw-bpm
 * {@code ProcessGraph} 先例，去掉 bpm 的 formKey——agent 图不绑定表单）。
 * </p>
 * <p>
 * <b>后端仅解释拓扑（id/kind/type/source/target），{@code config} 与 {@code style} 为不透明
 * Map 原样透传，严禁在后端解析其内部字段。</b>
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessGraph implements Serializable {

    /** 图业务 key（服务端生成，发布后冻结，与 sw_agent_graph_def.graph_key 一致）。 */
    private String graphKey;

    /** 图名称。 */
    private String name;

    /** 版本号（默认 1，与 def_version 对齐）。 */
    private Integer version;

    /** 图元素列表（节点 + 边）。 */
    private List<GraphElement> elements;

    /** 画布元数据（不透明，原样透传）。 */
    private Map<String, Object> canvas;
}
