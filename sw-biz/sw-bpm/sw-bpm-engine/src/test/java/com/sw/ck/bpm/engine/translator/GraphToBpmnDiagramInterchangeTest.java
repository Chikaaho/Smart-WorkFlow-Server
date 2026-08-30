package com.sw.ck.bpm.engine.translator;

import com.sw.ck.bpm.api.dto.GraphElement;
import com.sw.ck.bpm.api.dto.ProcessGraph;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DI（图形交换信息）回归：图模型不携带坐标，翻译器必须产出节点 Bounds 与边航点，
 * 否则 bpmn-js 渲染空白画布（第一轮验收 R-04 缺口：流程图整块空白）。
 */
@DisplayName("GraphToBpmnTranslator DI 自动布局")
class GraphToBpmnDiagramInterchangeTest {

    private final GraphToBpmnTranslator translator = new GraphToBpmnTranslator();

    private GraphElement node(String id, String type) {
        GraphElement e = new GraphElement();
        e.setId(id);
        e.setKind("node");
        e.setType(type);
        return e;
    }

    private GraphElement edge(String id, String source, String target) {
        GraphElement e = new GraphElement();
        e.setId(id);
        e.setKind("edge");
        e.setSource(source);
        e.setTarget(target);
        return e;
    }

    @Test
    @DisplayName("START→APPROVAL→END 翻译产物含节点 Bounds 与边航点")
    void shouldProduceNodeShapesAndEdgeWaypoints() {
        ProcessGraph graph = ProcessGraph.builder()
                .processKey("di_process")
                .name("DI Process")
                .elements(List.of(
                        node("node_start", "START"),
                        node("node_approval", "APPROVAL"),
                        node("node_end", "END"),
                        edge("edge_1", "node_start", "node_approval"),
                        edge("edge_2", "node_approval", "node_end")))
                .build();

        BpmnModel model = translator.translate(graph);

        // 每个节点都有 Bounds
        assertThat(model.getLocationMap()).containsKeys("node_start", "node_approval", "node_end");
        // 每条边都有航点
        assertThat(model.getFlowLocationMap()).containsKeys("edge_1", "edge_2");

        String xml = new String(new BpmnXMLConverter().convertToXML(model), StandardCharsets.UTF_8);
        // 转换为标准 BPMN XML 后：3 个 Shape + 2 个 Edge（各 2 航点）
        assertThat(xml).contains("BPMNShape_node_start");
        assertThat(xml).contains("BPMNShape_node_approval");
        assertThat(xml).contains("BPMNShape_node_end");
        assertThat(xml.split("BPMNEdge_", -1).length - 1).isEqualTo(2);
        assertThat(xml.split("<omgdi:waypoint", -1).length - 1).isEqualTo(4);
    }
}
