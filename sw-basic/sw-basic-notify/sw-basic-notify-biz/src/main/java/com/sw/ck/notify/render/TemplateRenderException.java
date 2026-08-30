package com.sw.ck.notify.render;

/**
 * 模板渲染失败（非法占位符或缺失变量）。
 * <p>
 * 方向 §3.3：失败必须发生在通知落库之前，不产生半成品通知——
 * 调用方（预览端点/发送服务）捕获后转 {@code BaseException(PARAM_ERROR)}
 * 返回明确错误信息。
 * </p>
 */
public class TemplateRenderException extends RuntimeException {

    public TemplateRenderException(String message) {
        super(message);
    }
}
