package com.sw.ck.bpm.engine.resolver;

import com.sw.ck.bpm.api.exception.BpmErrorCode;
import com.sw.ck.bpm.api.spi.assignee.NodeApproverContext;
import com.sw.ck.bpm.api.spi.assignee.NodeApproverResolver;
import com.sw.ck.bpm.api.spi.assignee.NodeApproverType;
import com.sw.ck.common.exception.BaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 固定审批人解析器。
 * <p>
 * 读取 {@link NodeApproverContext#getApproverValue()} 作为 userId 列表，
 * v1 取首个设为 assignee。
 * approverValue 预期为 {@code List<Integer>} 或 {@code List<String>} 或兼容格式。
 * </p>
 *
 * <h3>分发 Key</h3>
 * 注册为 {@link NodeApproverType#DESIGNATED}。
 */
@Component("designatedApproverResolver")
public class DesignatedApproverResolver implements NodeApproverResolver {

    private static final Logger log = LoggerFactory.getLogger(DesignatedApproverResolver.class);

    @Override
    @SuppressWarnings("unchecked")
    public List<String> resolve(NodeApproverContext context) {
        Object value = context.getApproverValue();
        if (value == null) {
            log.error("DESIGNATED approver value is null: nodeKey={}", context.getNodeKey());
            throw new BaseException(BpmErrorCode.APPROVER_RESOLVE_EMPTY.getCode(),
                    "固定审批人配置值为空");
        }

        List<String> userIds;
        if (value instanceof List<?> rawList) {
            userIds = rawList.stream()
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .toList();
        } else {
            // 单个 userId
            userIds = List.of(value.toString());
        }

        if (userIds.isEmpty()) {
            log.error("DESIGNATED approver value resolved to empty list: nodeKey={}", context.getNodeKey());
            throw new BaseException(BpmErrorCode.APPROVER_RESOLVE_EMPTY);
        }

        log.debug("DesignatedApproverResolver resolved {} approvers: nodeKey={}",
                userIds.size(), context.getNodeKey());
        return userIds;
    }
}
