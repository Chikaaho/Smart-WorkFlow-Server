package com.sw.ck.bpm.process.mapper;

import com.sw.ck.bpm.process.entity.BpmProcessDef;
import com.sw.ck.common.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程定义 Mapper —— 走 MyBatis-Plus 常规通道，@TableLogic + 租户拦截器自动生效。
 */
@Mapper
public interface BpmProcessDefMapper extends BaseMapperX<BpmProcessDef> {
}
