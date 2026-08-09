package com.sw.ck.bpm.process.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sw.ck.bpm.process.dto.InstanceFilterDTO;
import com.sw.ck.bpm.process.entity.BpmInstance;
import com.sw.ck.bpm.process.mapper.BpmInstanceMapper;
import com.sw.ck.bpm.process.service.BpmInstanceService;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 流程实例记录 Service 实现。
 */
@Service
public class BpmInstanceServiceImpl
        extends BaseServiceImpl<BpmInstanceMapper, BpmInstance>
        implements BpmInstanceService {

    @Override
    public Optional<BpmInstance> findByProcessInstanceId(String processInstanceId) {
        return Optional.ofNullable(
                lambdaQuery()
                        .eq(BpmInstance::getProcessInstanceId, processInstanceId)
                        .one()
        );
    }

    @Override
    public Optional<BpmInstance> findByBusinessKey(String businessKey) {
        return Optional.ofNullable(
                lambdaQuery()
                        .eq(BpmInstance::getBusinessKey, businessKey)
                        .one()
        );
    }

    @Override
    public void updateStatus(String processInstanceId, String status) {
        lambdaUpdate()
                .eq(BpmInstance::getProcessInstanceId, processInstanceId)
                .set(BpmInstance::getStatus, status)
                .update();
    }

    @Override
    public PageResult<BpmInstance> pageInstances(PageParam pageParam, InstanceFilterDTO filter) {
        // 构建条件（不含 ORDER BY——H2 PostgreSQL 模式不允许 COUNT + ORDER BY）
        LambdaQueryWrapper<BpmInstance> qw = new LambdaQueryWrapper<>();

        if (filter != null) {
            // 状态过滤
            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                qw.eq(BpmInstance::getStatus, filter.getStatus());
            }
            // 流程定义 key 过滤
            if (filter.getProcessDefKey() != null && !filter.getProcessDefKey().isBlank()) {
                qw.eq(BpmInstance::getProcessDefKey, filter.getProcessDefKey());
            }
            // 发起人过滤
            if (filter.getInitiatorId() != null) {
                qw.eq(BpmInstance::getInitiatorId, filter.getInitiatorId());
            }
        }

        // 查询总数（不含 ORDER BY）
        long total = getBaseMapper().selectCount(qw);

        // 分页查询（加 ORDER BY + LIMIT/OFFSET）
        qw.orderByDesc(BpmInstance::getCreateTime);
        long offset = (pageParam.getPageNum() - 1) * pageParam.getPageSize();
        int limit = (int) pageParam.getPageSize();
        List<BpmInstance> records = getBaseMapper().selectList(
                qw.last("LIMIT " + limit + " OFFSET " + offset));

        // 组装 PageResult
        PageResult<BpmInstance> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPageNum(pageParam.getPageNum());
        result.setPageSize(pageParam.getPageSize());
        return result;
    }
}
