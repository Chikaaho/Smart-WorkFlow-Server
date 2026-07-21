package com.sw.ck.job.scheduler;

import com.sw.ck.job.entity.JobInfo;
import com.sw.ck.job.service.JobInfoService;
import com.sw.ck.job.service.QuartzSchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时自动恢复 Quartz 定时任务调度。
 * <p>
 * Quartz 默认使用 RAMJobStore，应用重启后所有已注册的 Job 丢失。
 * 本 Runner 在应用启动完成后从数据库查询所有 NORMAL 状态的任务定义，
 * 并逐一重新注册到 Quartz 调度器。
 * </p>
 *
 * <h3>执行时机</h3>
 * {@link ApplicationRunner} 在所有 Bean 初始化完成、应用完全就绪后执行，
 * 确保 {@link QuartzSchedulerService} 和 {@link JobInfoService} 均可用。
 */
@Component
public class JobStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JobStartupRunner.class);

    private final JobInfoService jobInfoService;
    private final QuartzSchedulerService quartzSchedulerService;

    public JobStartupRunner(JobInfoService jobInfoService,
                            QuartzSchedulerService quartzSchedulerService) {
        this.jobInfoService = jobInfoService;
        this.quartzSchedulerService = quartzSchedulerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<JobInfo> enabledJobs = jobInfoService.listEnabled();
        if (enabledJobs.isEmpty()) {
            log.info("无待恢复的定时任务");
            return;
        }

        int successCount = 0;
        int failCount = 0;
        for (JobInfo jobInfo : enabledJobs) {
            try {
                // 跳过已在 Quartz 中注册的任务（防御性检查）
                if (quartzSchedulerService.exists(jobInfo)) {
                    log.info("任务已在 Quartz 中注册，跳过: jobId={}, jobName={}",
                            jobInfo.getId(), jobInfo.getJobName());
                    continue;
                }
                quartzSchedulerService.addJob(jobInfo);
                successCount++;
                log.info("定时任务已恢复: jobId={}, jobName={}, cron={}",
                        jobInfo.getId(), jobInfo.getJobName(), jobInfo.getCronExpression());
            } catch (Exception e) {
                failCount++;
                log.error("定时任务恢复失败: jobId={}, jobName={}, cron={}",
                        jobInfo.getId(), jobInfo.getJobName(), jobInfo.getCronExpression(), e);
            }
        }
        log.info("定时任务启动恢复完成: 总数={}, 成功={}, 失败={}",
                enabledJobs.size(), successCount, failCount);
    }
}
