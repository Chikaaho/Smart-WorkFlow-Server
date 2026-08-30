package com.sw.ck.bpm.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * BPMN 部署结果 —— 回填流程定义用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BpmDeployResult implements Serializable {

    /** Flowable 部署 ID（对应 ACT_RE_DEPLOYMENT.ID_）。 */
    private String deploymentId;

    /** Flowable 流程定义 ID（对应 ACT_RE_PROCDEF.ID_）。 */
    private String processDefinitionId;
}
