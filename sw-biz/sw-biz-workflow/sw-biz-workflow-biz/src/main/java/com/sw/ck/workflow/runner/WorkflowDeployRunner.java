package com.sw.ck.workflow.runner;

import com.sw.ck.common.constant.CommonConstants;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sw.ck.workflow.entity.WorkflowFormBinding;
import com.sw.ck.workflow.service.WorkflowFormBindingService;
import org.flowable.engine.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时部署 BPMN 并插入表单↔流程绑定。
 * <p>
 * 职责：
 * <ol>
 *   <li>在指定租户下部署 {@code skeleton_approval.bpmn20.xml}（单节点审批）</li>
 *   <li>检查并插入 {@code it_application} → {@code skeleton_approval} 的启用绑定</li>
 * </ol>
 * </p>
 *
 * <p>
 * 部署时手动设置 {@link LoginUserHolder} 以确保 MyBatis-Plus 拦截器
 * （审计字段填充 + 租户行级隔离）在插入绑定时正确生效。
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "sw.workflow", name = "enabled", havingValue = "true")
public class WorkflowDeployRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDeployRunner.class);

    /** 骨架流程定义 key */
    static final String PROCESS_KEY = "skeleton_approval";

    /** BPMN 类路径 */
    private static final String BPMN_RESOURCE = "processes/" + PROCESS_KEY + ".bpmn20.xml";

    /** IT申请 formKey（以 form 模块种子数据为准） */
    static final String IT_APPLICATION_FORM_KEY = "it_application";

    private final RepositoryService repositoryService;
    private final WorkflowFormBindingService bindingService;

    public WorkflowDeployRunner(RepositoryService repositoryService,
                                WorkflowFormBindingService bindingService) {
        this.repositoryService = repositoryService;
        this.bindingService = bindingService;
    }

    @Override
    public void run(String... args) {
        LoginUser systemUser = new LoginUser();
        systemUser.setUserId(CommonConstants.SYSTEM_OPERATOR_ID);
        systemUser.setTenantId(Long.parseLong(CommonConstants.SUPER_TENANT_ID));
        LoginUserHolder.set(systemUser);
        try {
            deployProcess();
            bindFormToProcess();
        } catch (Exception e) {
            log.error("启动部署/绑定失败，请确认 BPMN 资源和数据库状态", e);
        } finally {
            LoginUserHolder.clear();
        }
    }

    /** 部署 BPMN（Flowable 按内容去重，无需额外幂等检查） */
    private void deployProcess() {
        repositoryService.createDeployment()
                .addClasspathResource(BPMN_RESOURCE)
                .tenantId(CommonConstants.SUPER_TENANT_ID)
                .deploy();
        log.info("BPMN 部署完成: processKey={}, tenantId={}", PROCESS_KEY, CommonConstants.SUPER_TENANT_ID);
    }

    /** 插入 IT申请 → skeleton_approval 绑定（若已存在则跳过） */
    private void bindFormToProcess() {
        List<WorkflowFormBinding> existing = bindingService.findActiveByFormKey(IT_APPLICATION_FORM_KEY);
        if (!existing.isEmpty()) {
            log.info("绑定已存在: formKey={} → processDefKey={}, 跳过",
                    IT_APPLICATION_FORM_KEY, existing.get(0).getProcessDefKey());
            return;
        }

        WorkflowFormBinding binding = new WorkflowFormBinding();
        binding.setFormKey(IT_APPLICATION_FORM_KEY);
        binding.setProcessDefKey(PROCESS_KEY);
        binding.setActive(true);
        bindingService.save(binding);
        log.info("绑定已创建: formKey={} → processDefKey={}", IT_APPLICATION_FORM_KEY, PROCESS_KEY);
    }
}
