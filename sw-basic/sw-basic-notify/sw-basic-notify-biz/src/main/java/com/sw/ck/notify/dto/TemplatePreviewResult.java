package com.sw.ck.notify.dto;

import lombok.Data;

/**
 * 模板预览结果：渲染后的标题与正文（与真实发送落库内容同源同语义）。
 */
@Data
public class TemplatePreviewResult {

    /** 渲染后标题 */
    private String title;

    /** 渲染后正文 */
    private String content;

    public TemplatePreviewResult() {
    }

    public TemplatePreviewResult(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
