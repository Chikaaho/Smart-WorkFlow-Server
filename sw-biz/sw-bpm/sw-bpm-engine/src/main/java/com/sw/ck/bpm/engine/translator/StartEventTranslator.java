package com.sw.ck.bpm.engine.translator;

import com.sw.ck.bpm.api.dto.GraphElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.StartEvent;

/**
 * START 节点翻译器 —— 画布 START 节点 → BPMN {@link StartEvent}。
 * <p>
 * 自 B2（M04-F08-01）起从 {@link GraphToBpmnTranslator} 的 switch 中拆出，
 * 经 {@link NodeTypeTranslator} 注册表分发，翻译行为与拆分前逐字节一致。
 * </p>
 */
public class StartEventTranslator implements NodeTypeTranslator {

    @Override
    public String type() {
        return "START";
    }

    @Override
    public FlowElement translate(GraphElement node) {
        StartEvent startEvent = new StartEvent();
        startEvent.setId(node.getId());
        startEvent.setName("Start");
        return startEvent;
    }
}
