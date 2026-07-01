package com.sw.ck.bpm.api.exception;

import com.sw.ck.common.exception.ErrorCode;
import lombok.Getter;

/**
 * BPM 模块错误码（2000-2999 区间）。
 *
 * <p>本刀（cut A）仅落校验类 2000-2009；21xx 翻译/22xx 审批人区间留给 cut B。</p>
 */
@Getter
public enum BpmErrorCode implements ErrorCode {

    // ==================== 图校验（2000-2009） ====================
    GRAPH_MISSING_START(2000, "图缺少开始节点"),
    GRAPH_MULTIPLE_START(2001, "图存在多个开始节点"),
    GRAPH_MISSING_END(2002, "图缺少结束节点"),
    GRAPH_MULTIPLE_END(2003, "图存在多个结束节点"),
    GRAPH_NODE_EDGE_CARDINALITY(2004, "节点入/出边基数违规"),
    GRAPH_ORPHAN_NODE(2005, "存在孤儿/不可达节点"),
    GRAPH_EDGE_TARGET_NOT_FOUND(2006, "边指向不存在的节点"),
    GRAPH_ILLEGAL_EDGE(2007, "非法边（自环或重复边）"),
    GRAPH_UNKNOWN_NODE_TYPE(2008, "未注册的节点类型"),
    GRAPH_FORM_NOT_FOUND(2009, "绑定表单不存在"),

    // ==================== 通用（20xx） ====================
    PROCESS_DEF_NOT_FOUND(2010, "流程定义不存在"),

    // ==================== 翻译/发布（21xx） ====================
    FORM_NOT_PUBLISHED(2100, "绑定表单未发布，请先发布表单"),
    PROCESS_KEY_FROZEN(2101, "流程定义已有发布版本，process_key 不可变更"),
    TRANSLATION_FAILED(2102, "图翻译为 BPMN 失败"),
    DEPLOYMENT_FAILED(2103, "BPMN 部署失败"),

    // ==================== 审批人解析（22xx） ====================
    APPROVER_RESOLVE_EMPTY(2200, "审批人解析结果为空"),
    APPROVER_TYPE_NOT_IMPLEMENTED(2201, "审批人类型未实现"),
    APPROVER_CONFIG_MISSING(2202, "审批人配置缺失"),
    APPROVER_TENANT_ID_MISSING(2203, "流程变量中缺少 tenantId，无法构建审批人上下文"),
    ;

    private final int code;
    private final String message;

    BpmErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
