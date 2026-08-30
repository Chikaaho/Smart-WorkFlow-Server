package com.sw.ck.bpm.process.service.impl;

import com.sw.ck.bpm.process.entity.BpmFormBinding;
import com.sw.ck.bpm.process.mapper.BpmFormBindingMapper;
import com.sw.ck.bpm.process.service.BpmFormBindingService;
import com.sw.ck.common.service.BaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 表单↔流程绑定 Service 实现。
 */
@Service
public class BpmFormBindingServiceImpl
        extends BaseServiceImpl<BpmFormBindingMapper, BpmFormBinding>
        implements BpmFormBindingService {

    @Override
    public List<BpmFormBinding> findActiveByFormKey(String formKey) {
        return lambdaQuery()
                .eq(BpmFormBinding::getFormKey, formKey)
                .eq(BpmFormBinding::getActive, Boolean.TRUE)
                .list();
    }
}
