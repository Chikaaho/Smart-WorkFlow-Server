package com.sw.ck.bpm.process.model;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 节点类型注册表 —— 用 Map 查找替代 switch/if-else 链。
 * <p>
 * 新增节点类型只需在 {@link #registerDefaults()} 加一条注册项，
 * 校验器（{@code GraphValidator}）无需改动。
 * </p>
 * <p>
 * 完整注册骨架（M04-F08-01 BPM 可插拔机制）：除已落地可翻译的
 * START/END/APPROVAL 三类外，预留条件/网关类型注册位（CONDITION、
 * EXCLUSIVE_GATEWAY、PARALLEL_GATEWAY、JOIN_GATEWAY），
 * 供后续新增节点类型时仅注册即被校验器识别。
 * 预留位类型当前无对应翻译器，翻译器（{@code GraphToBpmnTranslator}）
 * 遇到未注册翻译器的类型按既有语义 warn + skip。
 * </p>
 * <p>线程安全：注册表在 {@code @PostConstruct} 中一次性填充，此后只读。</p>
 */
@Component
public class NodeTypeRegistry {

    private static final Logger log = LoggerFactory.getLogger(NodeTypeRegistry.class);

    private static final int UNLIMITED = Integer.MAX_VALUE;

    private final Map<String, NodeTypeSpec> registry = new HashMap<>();

    @PostConstruct
    void registerDefaults() {
        // START: 0 入 / 1 出（仅一条到首节点的边）
        register("START", new NodeTypeSpec(0, 0, 1, 1, true, false));

        // END: 1..N 入 / 0 出
        register("END", new NodeTypeSpec(1, UNLIMITED, 0, 0, true, false));

        // APPROVAL: 1 入 / 1 出
        register("APPROVAL", new NodeTypeSpec(1, 1, 1, 1, false, true));

        // ---- 预留位（完整注册骨架，暂未注册翻译器，仅供校验器识别） ----

        // CONDITION 条件判断节点: 1 入 / 1 出（串行条件，单进单出）
        register("CONDITION", new NodeTypeSpec(1, 1, 1, 1, false, true));

        // EXCLUSIVE_GATEWAY 排他网关: 1 入 / 2..N 出（单选分支）
        register("EXCLUSIVE_GATEWAY", new NodeTypeSpec(1, 1, 2, UNLIMITED, false, true));

        // PARALLEL_GATEWAY 并行网关: 1 入 / 2..N 出（并行扇出）
        register("PARALLEL_GATEWAY", new NodeTypeSpec(1, 1, 2, UNLIMITED, false, true));

        // JOIN_GATEWAY 汇合网关: 2..N 入 / 1 出（分支汇合）
        register("JOIN_GATEWAY", new NodeTypeSpec(2, UNLIMITED, 1, 1, false, true));

        log.info("NodeTypeRegistry initialized with {} node types", registry.size());
    }

    private void register(String type, NodeTypeSpec spec) {
        registry.put(type, spec);
    }

    /**
     * 获取指定类型的规格，未注册返回 null。
     */
    public NodeTypeSpec get(String type) {
        return registry.get(type);
    }

    /**
     * 判断类型是否已注册。
     */
    public boolean isRegistered(String type) {
        return registry.containsKey(type);
    }

    /**
     * 只读视图。
     */
    public Map<String, NodeTypeSpec> getAll() {
        return Collections.unmodifiableMap(registry);
    }
}
