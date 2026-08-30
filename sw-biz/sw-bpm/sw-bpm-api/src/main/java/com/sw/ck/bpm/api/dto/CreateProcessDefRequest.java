package com.sw.ck.bpm.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 创建流程定义请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProcessDefRequest implements Serializable {

    /** 流程名称。 */
    @NotBlank(message = "流程名称不能为空")
    private String name;

    /** 绑定表单 formKey。 */
    @NotBlank(message = "表单标识不能为空")
    private String formKey;
}
