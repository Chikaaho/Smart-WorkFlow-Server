package com.sw.ck.bpm.process.validator;

import com.sw.ck.bpm.api.dto.GraphElement;
import com.sw.ck.bpm.api.dto.GraphValidationError;
import com.sw.ck.bpm.api.exception.BpmErrorCode;
import com.sw.ck.bpm.api.node.BpmNodeDefinition;
import com.sw.ck.bpm.api.node.BpmNodeRegistry;
import com.sw.ck.form.api.form.FormDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 流程图画布校验器。
 * <p>
 * 仅解释图的拓扑结构（id/kind/type/source/target），不解析 config/style。
 * 类型判定、拓扑与配置能力均走 {@link BpmNodeRegistry} 的唯一注册结果，禁止平行类型表。
 * </p>
 */
@Component
public class GraphValidator {

    private static final Logger log = LoggerFactory.getLogger(GraphValidator.class);

    private static final String KIND_NODE = "node";
    private static final String KIND_EDGE = "edge";

    private final BpmNodeRegistry nodeRegistry;
    private final FormDefinitionService formDefinitionService;

    public GraphValidator(BpmNodeRegistry nodeRegistry, FormDefinitionService formDefinitionService) {
        this.nodeRegistry = nodeRegistry;
        this.formDefinitionService = formDefinitionService;
    }

    /**
     * 校验完整的 ProcessGraph。
     *
     * @param elements 图元素列表
     * @param formKey  绑定表单 formKey（可为 null，为 null 时跳过表单存在校验）
     * @return 校验错误列表，空列表表示通过
     */
    public List<GraphValidationError> validate(List<GraphElement> elements, String formKey) {
        List<GraphValidationError> errors = new ArrayList<>();

        if (elements == null || elements.isEmpty()) {
            errors.add(err(null, BpmErrorCode.GRAPH_MISSING_START));
            errors.add(err(null, BpmErrorCode.GRAPH_MISSING_END));
            return errors;
        }

        // 分离节点与边
        List<GraphElement> nodes = elements.stream()
                .filter(e -> KIND_NODE.equals(e.getKind()))
                .toList();
        List<GraphElement> edges = elements.stream()
                .filter(e -> KIND_EDGE.equals(e.getKind()))
                .toList();

        Set<String> nodeIds = nodes.stream()
                .map(GraphElement::getId)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);

        // --- 1. 恰好一个 START、一个 END ---
        List<GraphElement> starts = nodes.stream()
                .filter(this::isStartNode).toList();
        List<GraphElement> ends = nodes.stream()
                .filter(this::isEndNode).toList();

        if (starts.isEmpty()) {
            errors.add(err(null, BpmErrorCode.GRAPH_MISSING_START));
        } else if (starts.size() > 1) {
            for (GraphElement s : starts) {
                errors.add(err(s.getId(), BpmErrorCode.GRAPH_MULTIPLE_START));
            }
        }

        if (ends.isEmpty()) {
            errors.add(err(null, BpmErrorCode.GRAPH_MISSING_END));
        } else if (ends.size() > 1) {
            for (GraphElement e : ends) {
                errors.add(err(e.getId(), BpmErrorCode.GRAPH_MULTIPLE_END));
            }
        }

        // --- 2. 未知节点类型 ---
        for (GraphElement node : nodes) {
            String type = node.getType();
            if (type == null || nodeRegistry.find(type).isEmpty()) {
                errors.add(err(node.getId(), BpmErrorCode.GRAPH_UNKNOWN_NODE_TYPE));
            } else {
                errors.addAll(nodeRegistry.validateConfig(node));
            }
        }

        // --- 3. 边校验：source/target 存在、非自环、非重复 ---
        Set<String> edgeKeys = new HashSet<>();
        for (GraphElement edge : edges) {
            String source = edge.getSource();
            String target = edge.getTarget();

            // 指向不存在的节点
            if (source != null && !nodeIds.contains(source)) {
                errors.add(err(edge.getId(), BpmErrorCode.GRAPH_EDGE_TARGET_NOT_FOUND));
            }
            if (target != null && !nodeIds.contains(target)) {
                errors.add(err(edge.getId(), BpmErrorCode.GRAPH_EDGE_TARGET_NOT_FOUND));
            }

            // 自环
            if (source != null && source.equals(target)) {
                errors.add(err(edge.getId(), BpmErrorCode.GRAPH_ILLEGAL_EDGE));
            }

            // 重复边
            if (source != null && target != null) {
                String key = source + "->" + target;
                if (!edgeKeys.add(key)) {
                    errors.add(err(edge.getId(), BpmErrorCode.GRAPH_ILLEGAL_EDGE));
                }
            }
        }

        // --- 4. 节点入/出度基数校验 ---
        // 仅在 START/END 数量正确 + 类型全注册时才做基数校验（否则基数无意义）
        if (starts.size() == 1 && ends.size() == 1 && errors.stream()
                .noneMatch(e -> e.getErrorCode() == BpmErrorCode.GRAPH_UNKNOWN_NODE_TYPE.getCode())) {

            Map<String, Integer> inDegree = new HashMap<>();
            Map<String, Integer> outDegree = new HashMap<>();
            for (String nid : nodeIds) {
                inDegree.put(nid, 0);
                outDegree.put(nid, 0);
            }

            for (GraphElement edge : edges) {
                if (edge.getSource() != null && nodeIds.contains(edge.getSource())) {
                    outDegree.merge(edge.getSource(), 1, Integer::sum);
                }
                if (edge.getTarget() != null && nodeIds.contains(edge.getTarget())) {
                    inDegree.merge(edge.getTarget(), 1, Integer::sum);
                }
            }

            for (GraphElement node : nodes) {
                String type = node.getType();
                if (type == null) continue;
                BpmNodeDefinition definition = nodeRegistry.find(type).orElse(null);
                if (definition == null || definition.metadata() == null) continue;
                var topology = definition.metadata().topology();

                int in = inDegree.getOrDefault(node.getId(), 0);
                int out = outDegree.getOrDefault(node.getId(), 0);

                if (in < topology.minIncoming() || in > topology.maxIncoming()
                        || out < topology.minOutgoing() || out > topology.maxOutgoing()) {
                    errors.add(err(node.getId(), BpmErrorCode.GRAPH_NODE_EDGE_CARDINALITY));
                }
            }
        }

        // --- 5. 连通性（无孤儿） ---
        // 正向 BFS：从 START 出发可到达的所有节点
        // 反向 BFS：从 END 出发可反向到达的所有节点
        // 任一节点未被两者之一覆盖 → 孤儿
        if (starts.size() == 1 && ends.size() == 1) {
            // 构建邻接表
            Map<String, List<String>> forward = new HashMap<>();
            Map<String, List<String>> backward = new HashMap<>();
            for (String nid : nodeIds) {
                forward.put(nid, new ArrayList<>());
                backward.put(nid, new ArrayList<>());
            }
            for (GraphElement edge : edges) {
                String s = edge.getSource();
                String t = edge.getTarget();
                if (s != null && t != null && nodeIds.contains(s) && nodeIds.contains(t)) {
                    forward.get(s).add(t);
                    backward.get(t).add(s);
                }
            }

            String startId = starts.get(0).getId();
            String endId = ends.get(0).getId();

            Set<String> forwardReachable = bfs(forward, startId);
            Set<String> backwardReachable = bfs(backward, endId);

            for (String nid : nodeIds) {
                if (!forwardReachable.contains(nid) || !backwardReachable.contains(nid)) {
                    errors.add(err(nid, BpmErrorCode.GRAPH_ORPHAN_NODE));
                }
            }
        }

        // --- 6. 表单存在校验 ---
        if (formKey != null && !formKey.isBlank()) {
            if (!formDefinitionService.formExists(formKey)) {
                errors.add(err(null, BpmErrorCode.GRAPH_FORM_NOT_FOUND));
            }
        }

        return errors;
    }

    /**
     * 从 startNode 出发的 BFS 遍历。
     */
    private Set<String> bfs(Map<String, List<String>> adj, String startNode) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        if (adj.containsKey(startNode)) {
            queue.add(startNode);
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) continue;
            for (String neighbor : adj.getOrDefault(current, Collections.emptyList())) {
                if (!visited.contains(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }

    private GraphValidationError err(String elementId, BpmErrorCode errorCode) {
        return GraphValidationError.builder()
                .elementId(elementId)
                .errorCode(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    private boolean isStartNode(GraphElement node) {
        return nodeRegistry.find(node.getType())
                .map(definition -> definition.metadata().startNode())
                .orElse(false);
    }

    private boolean isEndNode(GraphElement node) {
        return nodeRegistry.find(node.getType())
                .map(definition -> definition.metadata().endNode())
                .orElse(false);
    }
}
