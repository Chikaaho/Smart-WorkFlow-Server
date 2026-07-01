package com.sw.ck.bpm.process.mapper;

import com.sw.ck.bpm.process.entity.BpmInstance;
import com.sw.ck.common.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程实例记录 Mapper。
 */
@Mapper
public interface BpmInstanceMapper extends BaseMapperX<BpmInstance> {
}
