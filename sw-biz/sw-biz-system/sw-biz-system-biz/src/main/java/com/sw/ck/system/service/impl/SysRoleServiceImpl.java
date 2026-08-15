package com.sw.ck.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.system.entity.SysRole;
import com.sw.ck.system.entity.SysRoleDept;
import com.sw.ck.system.mapper.SysRoleDeptMapper;
import com.sw.ck.system.mapper.SysRoleMapper;
import com.sw.ck.system.service.SysRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 系统角色 Service 实现。
 */
@Service
public class SysRoleServiceImpl
        extends BaseServiceImpl<SysRoleMapper, SysRole>
        implements SysRoleService {

    private final SysRoleDeptMapper sysRoleDeptMapper;

    public SysRoleServiceImpl(SysRoleDeptMapper sysRoleDeptMapper) {
        this.sysRoleDeptMapper = sysRoleDeptMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysRole role) {
        // 校验编码唯一性
        if (getByCode(role.getCode()) != null) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "角色编码已存在");
        }
        save(role);
        // 角色部门关联（CUSTOM 数据范围的可见部门集合）
        insertRoleDepts(role.getId(), role.getDeptIds());
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysRole role) {
        // 校验编码唯一性（排除自身）
        SysRole existing = getByCode(role.getCode());
        if (existing != null && !existing.getId().equals(role.getId())) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "角色编码已存在");
        }
        updateById(role);
        // 角色部门关联先删后插（事务内）
        sysRoleDeptMapper.delete(
                new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, role.getId()));
        insertRoleDepts(role.getId(), role.getDeptIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        removeById(id);
    }

    @Override
    public PageResult<SysRole> page(PageParam pageParam, SysRole query) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getName())) {
                wrapper.like(SysRole::getName, query.getName());
            }
            if (StringUtils.isNotBlank(query.getCode())) {
                wrapper.like(SysRole::getCode, query.getCode());
            }
            if (query.getStatus() != null) {
                wrapper.eq(SysRole::getStatus, query.getStatus());
            }
        }
        wrapper.orderByAsc(SysRole::getCreateTime);
        PageResult<SysRole> pageResult = baseMapper.selectPage(pageParam, wrapper);
        // 列表回填 deptIds（供前端回显）
        fillDeptIds(pageResult.getRecords());
        return pageResult;
    }

    @Override
    public SysRole getById(java.io.Serializable id) {
        SysRole role = super.getById(id);
        if (role != null) {
            role.setDeptIds(listDeptIds(role.getId()));
        }
        return role;
    }

    @Override
    public SysRole getByCode(String code) {
        return lambdaQuery().eq(SysRole::getCode, code).one();
    }

    /**
     * 写入角色部门关联（去重，空/null 集合不写入任何行）。
     */
    private void insertRoleDepts(Long roleId, List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return;
        }
        List<Long> distinctDeptIds = deptIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        for (Long deptId : distinctDeptIds) {
            SysRoleDept roleDept = new SysRoleDept();
            roleDept.setRoleId(roleId);
            roleDept.setDeptId(deptId);
            sysRoleDeptMapper.insert(roleDept);
        }
    }

    /**
     * 批量回填 deptIds：一次查询本页全部角色的关联，按 roleId 分组。
     */
    private void fillDeptIds(List<SysRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        List<Long> roleIds = roles.stream()
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> deptIdsByRole = sysRoleDeptMapper.selectList(
                        new LambdaQueryWrapper<SysRoleDept>().in(SysRoleDept::getRoleId, roleIds))
                .stream()
                .filter(rd -> rd.getRoleId() != null && rd.getDeptId() != null)
                .collect(Collectors.groupingBy(SysRoleDept::getRoleId,
                        LinkedHashMap::new,
                        Collectors.mapping(SysRoleDept::getDeptId, Collectors.toList())));
        for (SysRole role : roles) {
            role.setDeptIds(deptIdsByRole.getOrDefault(role.getId(), Collections.emptyList()));
        }
    }

    /**
     * 查询单个角色的关联部门 ID 列表。
     */
    private List<Long> listDeptIds(Long roleId) {
        return sysRoleDeptMapper.selectList(
                        new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId))
                .stream()
                .map(SysRoleDept::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
