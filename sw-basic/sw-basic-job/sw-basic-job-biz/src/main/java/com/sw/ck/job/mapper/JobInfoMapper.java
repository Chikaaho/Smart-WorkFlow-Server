package com.sw.ck.job.mapper;

import com.sw.ck.common.mapper.BaseMapperX;
import com.sw.ck.job.entity.JobInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务定义 Mapper。
 */
@Mapper
public interface JobInfoMapper extends BaseMapperX<JobInfo> {
}
