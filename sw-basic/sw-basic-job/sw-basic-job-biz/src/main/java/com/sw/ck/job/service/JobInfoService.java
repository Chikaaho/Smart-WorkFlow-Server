package com.sw.ck.job.service;

import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseService;
import com.sw.ck.job.entity.JobInfo;

import java.util.List;

/**
 * 定时任务定义 Service。
 */
public interface JobInfoService extends BaseService<JobInfo> {

    /**
     * 按任务名称查询（同租户内名称唯一）。
     *
     * @param jobName 任务名称
     * @return 任务定义，不存在返回 null
     */
    JobInfo getByJobName(String jobName);

    /**
     * 查询所有启用中的任务（status=NORMAL 且 deleted=0）。
     *
     * @return 启用中的任务列表
     */
    List<JobInfo> listEnabled();

    /**
     * 分页查询任务定义。
     *
     * @param pageParam 分页参数
     * @param query     查询条件
     * @return 分页结果
     */
    PageResult<JobInfo> page(PageParam pageParam, JobInfo query);
}
