package com.sw.ck.notify.dto;

import lombok.Data;

import java.util.Map;

/**
 * 模板预览请求：模板内容 + 变量值表（也可用于发送前渲染校验）。
 */
@Data
public class TemplatePreviewRequest {

    /** 标题模板 */
    private String titleTemplate;

    /** 正文模板 */
    private String contentTemplate;

    /** 变量值（key=变量名，value=纯文本） */
    private Map<String, String> variables;
}
