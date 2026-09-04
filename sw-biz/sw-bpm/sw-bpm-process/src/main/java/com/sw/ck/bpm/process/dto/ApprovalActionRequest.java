package com.sw.ck.bpm.process.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 审批动作请求；旧的无 body complete/reject 调用保持可用。 */
@Data
public class ApprovalActionRequest {

    private ApprovalAction action;
    private String returnTargetNodeId;
    private String opinionFormId;
    private String opinionFormVersion;
    private String comment;
    private Map<String, Object> opinionData = new LinkedHashMap<>();
}
