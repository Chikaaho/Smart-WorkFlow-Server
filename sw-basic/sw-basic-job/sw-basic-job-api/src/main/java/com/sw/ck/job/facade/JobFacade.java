package com.sw.ck.job.facade;

import com.sw.ck.job.dto.JobInfoDTO;

/**
 * 定时任务门面接口。
 * <p>
 * 定义于 {@code -api} 模块，实现于 {@code -biz} 模块。
 * 供其他模块（如 BPM）通过 Facade 模式查询任务信息。
 * </p>
 */
public interface JobFacade {

    /**
     * 按 ID 查询任务定义。
     *
     * @param jobId 任务 ID
     * @return 任务定义 DTO，不存在返回 null
     */
    JobInfoDTO getById(Long jobId);

    /**
     * 按名称查询任务定义。
     *
     * @param jobName 任务名称
     * @return 任务定义 DTO，不存在返回 null
     */
    JobInfoDTO getByJobName(String jobName);
}
