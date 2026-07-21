package com.sw.ck.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.job.entity.JobInfo;
import com.sw.ck.job.mapper.JobInfoMapper;
import com.sw.ck.job.service.JobInfoService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 定时任务定义 Service 实现。
 */
@Service
public class JobInfoServiceImpl
        extends BaseServiceImpl<JobInfoMapper, JobInfo>
        implements JobInfoService {

    @Override
    public JobInfo getByJobName(String jobName) {
        return lambdaQuery()
                .eq(JobInfo::getJobName, jobName)
                .one();
    }

    @Override
    public List<JobInfo> listEnabled() {
        return lambdaQuery()
                .eq(JobInfo::getStatus, "NORMAL")
                .list();
    }

    @Override
    public PageResult<JobInfo> page(PageParam pageParam, JobInfo query) {
        Page<JobInfo> mpPage = new Page<>(pageParam.getPageNum(), pageParam.getPageSize());
        LambdaQueryWrapper<JobInfo> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getJobName() != null && !query.getJobName().isBlank()) {
                wrapper.like(JobInfo::getJobName, query.getJobName());
            }
            if (query.getJobType() != null && !query.getJobType().isBlank()) {
                wrapper.eq(JobInfo::getJobType, query.getJobType());
            }
            if (query.getStatus() != null && !query.getStatus().isBlank()) {
                wrapper.eq(JobInfo::getStatus, query.getStatus());
            }
        }
        wrapper.orderByDesc(JobInfo::getCreateTime);
        Page<JobInfo> result = page(mpPage, wrapper);
        return PageResult.of(result);
    }
}
