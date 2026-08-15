package com.sw.ck.bpm.engine.translator;

import com.sw.ck.bpm.api.dto.GraphElement;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.FlowElement;

/**
 * END 节点翻译器 —— 画布 END 节点 → BPMN {@link EndEvent}。
 * <p>
 * 自 B2（M04-F08-01）起从 {@link GraphToBpmnTranslator} 的 switch 中拆出，
 * 经 {@link NodeTypeTranslator} 注册表分发，翻译行为与拆分前逐字节一致。
 * </p>
 */
public class EndEventTranslator implements NodeTypeTranslator {

    @Override
    public String type() {
        return "END";
    }

    @Override
    public FlowElement translate(GraphElement node) {
        EndEvent endEvent = new EndEvent();
        endEvent.setId(node.getId());
        endEvent.setName("End");
        return endEvent;
    }
}
