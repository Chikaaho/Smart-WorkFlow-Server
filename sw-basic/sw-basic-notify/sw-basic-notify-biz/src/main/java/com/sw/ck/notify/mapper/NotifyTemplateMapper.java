package com.sw.ck.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sw.ck.notify.entity.NotifyTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息模板 Mapper。
 * <p>
 * 租户条件由 {@code TenantLineHandler} 在 SQL 层自动注入；
 * 逻辑删除由 MyBatis-Plus {@code @TableLogic}（BaseEntity）承接。
 * </p>
 */
@Mapper
public interface NotifyTemplateMapper extends BaseMapper<NotifyTemplate> {
}
