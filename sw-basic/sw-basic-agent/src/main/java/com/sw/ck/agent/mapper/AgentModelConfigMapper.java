package com.sw.ck.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.common.datascope.DataScopeFilter;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 大模型接入配置 Mapper。
 */
public interface AgentModelConfigMapper extends BaseMapper<AgentModelConfig> {

    /**
     * 模型配置分页查询（数据范围纳管入口）。
     * <p>
     * sw_agent_model_config 无 dept_id 列，归属用户列为 create_by（VARCHAR(64)，
     * 存的是 userId 的字符串形态），无法用 {@code @DataScope} 标注（handler 部门档拼接
     * dept_id、SELF 档拼接 bigint 字面量，均与该列不兼容），故在本方法内以等效条件实现：
     * SELF → create_by = CAST(userId AS VARCHAR)；部门三档 → create_by IN
     * (SELECT CAST(id AS VARCHAR) FROM sys_user WHERE dept_id IN (...))；空集恒假。
     * scope 由 Service 经 {@link DataScopeFilter#resolve} 从既有 SPI 解析后传入。
     * 逻辑删除条件手动拼接。
     * </p>
     */
    @Select("""
            <script>
            SELECT * FROM sw_agent_model_config
            <where>
              deleted = 0
              <if test="nameKeyword != null and nameKeyword != ''">AND name LIKE CONCAT('%', #{nameKeyword}, '%')</if>
              <if test="scope.userId != null">AND create_by = CAST(#{scope.userId} AS VARCHAR)</if>
              <if test="scope.deptIds != null and scope.deptIds.size() > 0">
                AND create_by IN (SELECT CAST(id AS VARCHAR) FROM sys_user WHERE dept_id IN
                <foreach collection="scope.deptIds" item="did" open="(" separator="," close=")">#{did}</foreach>)
              </if>
              <if test="scope.deptIds != null and scope.deptIds.isEmpty()">AND 1 = 0</if>
              <if test="scope.alwaysFalse">AND 1 = 0</if>
            </where>
            ORDER BY id DESC
            </script>
            """)
    IPage<AgentModelConfig> selectModelConfigPage(Page<AgentModelConfig> page,
                                                  @Param("nameKeyword") String nameKeyword,
                                                  @Param("scope") DataScopeFilter scope);
}
