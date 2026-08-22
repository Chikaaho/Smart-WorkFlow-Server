package com.sw.ck.agent.service;

import com.sw.ck.agent.dto.AgentGraphBreakpointsReq;
import com.sw.ck.agent.dto.AgentGraphDebugNodeDTO;
import com.sw.ck.agent.dto.AgentGraphDebugSessionDTO;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;

import java.util.List;
import java.util.Set;

/**
 * 图调试服务接口（M07-F02-04 单步调试）。
 */
public interface AgentGraphDebugService {

    AgentGraphDebugSessionDTO createSession(Long graphDefId, String input);

    AgentGraphDebugSessionDTO getSession(Long sessionId);

    AgentGraphDebugSessionDTO step(Long sessionId, Long expectedVersion);

    default AgentGraphDebugSessionDTO step(Long sessionId) {
        return step(sessionId, null);
    }

    AgentGraphDebugSessionDTO continueUntilBreakpoint(Long sessionId);

    AgentGraphDebugSessionDTO updateBreakpoints(Long sessionId, Set<String> breakpoints);

    AgentGraphDebugSessionDTO stop(Long sessionId);

    List<AgentGraphDebugNodeDTO> listNodes(Long sessionId);

    PageResult<AgentGraphDebugSessionDTO> pageSessions(PageParam pageParam, Long graphDefId);
}
