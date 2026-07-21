package com.sw.ck.job.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.job.entity.JobLog;
import com.sw.ck.job.mapper.JobLogMapper;
import com.sw.ck.job.service.JobLogService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 定时任务执行日志 Service 实现。
 */
@Service
public class JobLogServiceImpl
        extends BaseServiceImpl<JobLogMapper, JobLog>
        implements JobLogService {

    @Override
    public List<JobLog> listByJobId(Long jobId) {
        return lambdaQuery()
                .eq(JobLog::getJobId, jobId)
                .orderByDesc(JobLog::getCreateTime)
                .list();
    }

    @Override
    public JobLog getLatestByJobId(Long jobId) {
        return lambdaQuery()
                .eq(JobLog::getJobId, jobId)
                .orderByDesc(JobLog::getCreateTime)
                .last("LIMIT 1")
                .one();
    }

    @Override
    public PageResult<JobLog> page(PageParam pageParam, Long jobId) {
        Page<JobLog> mpPage = new Page<>(pageParam.getPageNum(), pageParam.getPageSize());
        Page<JobLog> result = lambdaQuery()
                .eq(JobLog::getJobId, jobId)
                .orderByDesc(JobLog::getCreateTime)
                .page(mpPage);
        return PageResult.of(result);
    }
}
