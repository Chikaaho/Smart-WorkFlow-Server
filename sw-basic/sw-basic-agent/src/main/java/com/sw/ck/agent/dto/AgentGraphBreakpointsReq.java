package com.sw.ck.agent.dto;

import lombok.Data;

import java.util.Set;

/**
 * 更新断点请求（M07-F02-04）。
 */
@Data
public class AgentGraphBreakpointsReq {

    /** 断点节点 id 集合（空集合 = 清空断点） */
    private Set<String> breakpoints;
}
