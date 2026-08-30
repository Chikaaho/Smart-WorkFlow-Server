package com.sw.ck.job.service;

import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseService;
import com.sw.ck.job.entity.JobLog;

import java.util.List;

/**
 * 定时任务执行日志 Service。
 */
public interface JobLogService extends BaseService<JobLog> {

    /**
     * 按任务 ID 查询日志列表（按创建时间倒序）。
     *
     * @param jobId 任务 ID
     * @return 日志列表
     */
    List<JobLog> listByJobId(Long jobId);

    /**
     * 查询最近一条执行记录。
     *
     * @param jobId 任务 ID
     * @return 最近日志，无记录返回 null
     */
    JobLog getLatestByJobId(Long jobId);

    /**
     * 按任务 ID 分页查询执行日志。
     *
     * @param pageParam 分页参数
     * @param jobId     任务 ID
     * @return 分页结果
     */
    PageResult<JobLog> page(PageParam pageParam, Long jobId);
}
