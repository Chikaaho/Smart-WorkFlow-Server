package com.sw.ck.bpm.process.mapper;

import com.sw.ck.bpm.process.entity.BpmFormBinding;
import com.sw.ck.common.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单↔流程绑定 Mapper。
 */
@Mapper
public interface BpmFormBindingMapper extends BaseMapperX<BpmFormBinding> {
}
