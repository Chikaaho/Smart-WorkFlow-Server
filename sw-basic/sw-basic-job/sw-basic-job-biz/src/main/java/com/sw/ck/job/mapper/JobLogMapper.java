package com.sw.ck.job.mapper;

import com.sw.ck.common.mapper.BaseMapperX;
import com.sw.ck.job.entity.JobLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务执行日志 Mapper。
 */
@Mapper
public interface JobLogMapper extends BaseMapperX<JobLog> {
}
