package com.sw.ck.system.service.impl;

import com.sw.ck.system.api.dict.DictFacade;
import com.sw.ck.system.api.dict.DictItemDTO;
import com.sw.ck.system.entity.SysDictData;
import com.sw.ck.system.service.SysDictDataService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DictFacade 实现。
 * <p>
 * 其它模块通过 {@link com.sw.ck.system.api.dict.DictFacade} 接口消费字典数据，
 * 禁止直接访问 sys_dict_data 表或 Mapper。
 * </p>
 */
@Service
public class DictFacadeImpl implements DictFacade {

    private final SysDictDataService sysDictDataService;

    public DictFacadeImpl(SysDictDataService sysDictDataService) {
        this.sysDictDataService = sysDictDataService;
    }

    @Override
    public List<DictItemDTO> listByType(String dictType) {
        List<SysDictData> list = sysDictDataService.listByDictCode(dictType);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isValidCode(String dictType, String code) {
        return sysDictDataService.isValidCode(dictType, code);
    }

    @Override
    public String resolveLabel(String dictType, String code) {
        return sysDictDataService.resolveLabel(dictType, code);
    }

    private DictItemDTO toDTO(SysDictData data) {
        return DictItemDTO.builder()
                .dictType(data.getDictCode())
                .code(data.getDictValue())
                .label(data.getLabel())
                .sort(data.getSort())
                .status(data.getStatus())
                .isDefault(data.getIsDefault())
                .cssClass(data.getCssClass())
                .listClass(data.getListClass())
                .build();
    }
}
