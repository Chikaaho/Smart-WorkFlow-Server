package com.sw.ck.bpm.api.spi.assignee;

/**
 * 审批人类型常量。
 * <p>
 * 作为 {@code Map<String, NodeApproverResolver>} 的分发 key。
 * </p>
 */
public final class NodeApproverType {

    /** 固定审批人：approverValue 为 userId 数组，v1 取首个。 */
    public static final String DESIGNATED = "DESIGNATED";

    /** 脚本审批人（本刀仅留桩，不实现表达式求值）。 */
    public static final String SCRIPT = "SCRIPT";

    private NodeApproverType() {
    }
}
