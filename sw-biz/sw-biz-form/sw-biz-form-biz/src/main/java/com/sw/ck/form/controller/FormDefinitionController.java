package com.sw.ck.form.controller;

import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.response.R;
import com.sw.ck.form.api.dto.FormConfigSaveReq;
import com.sw.ck.form.api.dto.FormCreateReq;
import com.sw.ck.form.api.dto.FormDefDTO;
import com.sw.ck.form.api.dto.FormUpdateReq;
import com.sw.ck.form.api.exception.FormErrorCode;
import com.sw.ck.form.service.FormDefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 表单定义管理 + 渲染接口。
 * <p>
 * 草稿和已发布都能取（前端设计器预览用草稿，填单用已发布）。
 * </p>
 */
@RestController
@RequestMapping("/api/form/def")
public class FormDefinitionController {

    private static final Logger log = LoggerFactory.getLogger(FormDefinitionController.class);

    private final FormDefService formDefService;

    public FormDefinitionController(FormDefService formDefService) {
        this.formDefService = formDefService;
    }

    // ==================== 草稿管理 ====================

    /**
     * 创建表单草稿。
     */
    @PostMapping
    public R<FormDefDTO> createDraft(@RequestBody FormCreateReq req) {
        log.info("Creating form draft: formKey={}, name={}", req.getFormKey(), req.getName());
        FormDefDTO result = formDefService.createDraft(
                req.getFormKey(), req.getName(),
                req.getLogicalTableName(), req.getDescription());
        return R.ok(result);
    }

    /**
     * 更新表单草稿。
     */
    @PutMapping("/{id}")
    public R<FormDefDTO> updateDraft(@PathVariable("id") String id, @RequestBody FormUpdateReq req) {
        log.info("Updating form draft: id={}", id);
        FormDefDTO result = formDefService.updateDraft(
                id, req.getName(), req.getLogicalTableName(), req.getDescription());
        return R.ok(result);
    }

    /**
     * 保存表单配置（definition JSON）。
     */
    @PostMapping("/{id}/config")
    public R<Void> saveConfig(@PathVariable("id") String id, @RequestBody FormConfigSaveReq req) {
        log.info("Saving form config: id={}", id);
        formDefService.saveConfig(id, req.getDefinition());
        return R.ok();
    }

    // ==================== 发布 ====================

    /**
     * 发布表单草稿。
     *
     * @param id         表单 ID
     * @param fieldSpecs 字段规格 JSON（前端设计器提供的字段定义）
     */
    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable("id") String id, @RequestBody String fieldSpecs) {
        log.info("Publishing form: id={}", id);
        formDefService.publish(id, fieldSpecs);
        return R.ok();
    }

    // ==================== 查询（渲染接口） ====================

    /**
     * 根据 ID 获取表单定义 DTO。
     */
    @GetMapping("/{id}")
    public R<FormDefDTO> getFormDef(@PathVariable("id") String id) {
        FormDefDTO dto = formDefService.getFormDef(id);
        if (dto == null) {
            return R.fail(FormErrorCode.FORM_NOT_FOUND.getCode(), FormErrorCode.FORM_NOT_FOUND.getMessage());
        }
        return R.ok(dto);
    }

    /**
     * 根据 formKey 获取表单定义 DTO。
     */
    @GetMapping("/by-key/{formKey}")
    public R<FormDefDTO> getFormDefByKey(@PathVariable("formKey") String formKey) {
        FormDefDTO dto = formDefService.getFormDefByKey(formKey);
        if (dto == null) {
            return R.fail(FormErrorCode.FORM_NOT_FOUND.getCode(), FormErrorCode.FORM_NOT_FOUND.getMessage());
        }
        return R.ok(dto);
    }

    /**
     * 渲染接口：根据 ID 获取表单 definition JSON。
     * <p>
     * 草稿和已发布均可获取。前端设计器预览用草稿，填单引擎用已发布。
     * </p>
     */
    @GetMapping("/{id}/definition")
    public R<String> getDefinition(@PathVariable("id") String id) {
        String definition = formDefService.getDefinitionById(id);
        if (definition == null) {
            return R.fail(FormErrorCode.CONFIG_NOT_FOUND.getCode(), FormErrorCode.CONFIG_NOT_FOUND.getMessage());
        }
        return R.ok(definition);
    }

    /**
     * 渲染接口：根据 formKey 获取表单 definition JSON。
     */
    @GetMapping("/by-key/{formKey}/definition")
    public R<String> getDefinitionByKey(@PathVariable("formKey") String formKey) {
        String definition = formDefService.getDefinition(formKey);
        if (definition == null) {
            return R.fail(FormErrorCode.CONFIG_NOT_FOUND.getCode(), FormErrorCode.CONFIG_NOT_FOUND.getMessage());
        }
        return R.ok(definition);
    }
}
