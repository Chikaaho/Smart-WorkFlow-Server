package com.sw.ck.bpm.process.validator;

import com.sw.ck.bpm.api.dto.GraphElement;
import com.sw.ck.bpm.api.dto.GraphValidationError;
import com.sw.ck.bpm.api.exception.BpmErrorCode;
import com.sw.ck.bpm.process.model.NodeTypeRegistry;
import com.sw.ck.form.api.form.FormDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * GraphValidator 单元测试 —— 重点覆盖正向 BFS 可达但反向 BFS 到不了 END 的死胡同节点。
 */
class GraphValidatorTest {

    private GraphValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        NodeTypeRegistry registry = new NodeTypeRegistry();
        // registerDefaults() 为 package-private，反射调用
        Method m = NodeTypeRegistry.class.getDeclaredMethod("registerDefaults");
        m.setAccessible(true);
        m.invoke(registry);
        // formDefinitionService 在 formKey=null 时不会被调用，safe-null mock
        validator = new GraphValidator(registry, mock(FormDefinitionService.class));
    }

    /**
     * 图拓扑：
     * <pre>
     *   START → A → B → END
     *            ↘
     *              C（死胡同 —— 从 START 可到达，但无法到达 END）
     * </pre>
     * <p>
     * 正向 BFS（从 START 出发）：{START, A, B, C, END}
     * 反向 BFS（从 END 出发）：{END, B, A, START}
     * C 在正向集合但不在反向集合 → 孤儿（2005）。
     * </p>
     * <p>
     * 注：此图同时触发 A（out=2）和 C（out=0）的 APPROVAL 入/出度基数违规（2004），
     * 这是预期行为——校验器独立运行所有规则，不因基数违规跳过连通性校验。
     * </p>
     */
    @Test
    void shouldDetectNodeReachableFromStartButCannotReachEnd() {
        String startId = "start";
        String endId = "end";
        String aId = "A";
        String bId = "B";
        String cId = "C";

        GraphElement start = node(startId, "START");
        GraphElement end = node(endId, "END");
        GraphElement a = node(aId, "APPROVAL");
        GraphElement b = node(bId, "APPROVAL");
        GraphElement c = node(cId, "APPROVAL");

        List<GraphElement> elements = List.of(
                start, end, a, b, c,
                edge("e1", startId, aId),
                edge("e2", aId, bId),
                edge("e3", bId, endId),
                edge("e4", aId, cId)   // 死胡同分支
        );

        List<GraphValidationError> errors = validator.validate(elements, null);

        // C 应被标记为孤儿（2005）
        assertThat(errors)
                .filteredOn(e -> e.getErrorCode() == BpmErrorCode.GRAPH_ORPHAN_NODE.getCode())
                .extracting(GraphValidationError::getElementId)
                .contains(cId);

        // START / END / A / B 不应被标记为孤儿
        assertThat(errors)
                .filteredOn(e -> e.getErrorCode() == BpmErrorCode.GRAPH_ORPHAN_NODE.getCode())
                .extracting(GraphValidationError::getElementId)
                .doesNotContain(startId, endId, aId, bId);
    }

    // ==================== helper ====================

    private static GraphElement node(String id, String type) {
        return GraphElement.builder()
                .id(id)
                .kind("node")
                .type(type)
                .config(Collections.emptyMap())
                .style(Collections.emptyMap())
                .build();
    }

    private static GraphElement edge(String id, String source, String target) {
        return GraphElement.builder()
                .id(id)
                .kind("edge")
                .source(source)
                .target(target)
                .config(Collections.emptyMap())
                .style(Collections.emptyMap())
                .build();
    }
}
