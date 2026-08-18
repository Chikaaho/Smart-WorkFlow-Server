package com.sw.ck.job.controller;

import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.response.R;
import com.sw.ck.job.entity.JobLog;
import com.sw.ck.job.service.JobLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 定时任务执行日志控制器。
 * <p>
 * 提供按任务 ID 查询执行日志分页列表与单条详情。
 * </p>
 */
@RestController
@RequestMapping("/job/log")
public class JobLogController {

    private static final Logger log = LoggerFactory.getLogger(JobLogController.class);

    private final JobLogService jobLogService;

    public JobLogController(JobLogService jobLogService) {
        this.jobLogService = jobLogService;
    }

    /**
     * 分页查询执行日志（按任务 ID 筛选）。
     *
     * @param jobId   任务 ID（必填）
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页日志列表
     */
    @PostMapping("/page")
    @PreAuthorize("@ss.hasPermi('job:log')")
    public R<PageResult<JobLog>> page(@RequestParam Long jobId,
                                       @RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize) {
        if (jobId == null) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR.getCode(), "任务 ID 不能为空");
        }
        PageParam pageParam = new PageParam();
        pageParam.setPageNum(pageNum);
        pageParam.setPageSize(pageSize);
        // 按 jobId 查询，按创建时间倒序
        return R.ok(jobLogService.page(pageParam, jobId));
    }

    /**
     * 按 ID 查询单条日志详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('job:log')")
    public R<JobLog> getById(@PathVariable Long id) {
        JobLog jobLog = jobLogService.getById(id);
        if (jobLog == null) {
            throw new BaseException(CommonErrorCode.NOT_FOUND.getCode(), "日志不存在");
        }
        return R.ok(jobLog);
    }
}
