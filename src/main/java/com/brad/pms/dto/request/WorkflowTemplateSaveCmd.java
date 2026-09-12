package com.brad.pms.dto.request;

import com.brad.pms.workflow.WorkflowTemplateDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowTemplateSaveCmd {
    private Long projectTypeId;
    @NotBlank(message = "流程模板名称不能为空")
    @Size(max = 160, message = "流程模板名称不能超过160个字符")
    private String name;
    @Size(max = 500, message = "流程模板说明不能超过500个字符")
    private String description;
    @NotNull(message = "流程节点定义不能为空")
    @Valid
    private WorkflowTemplateDefinition definition;
}
