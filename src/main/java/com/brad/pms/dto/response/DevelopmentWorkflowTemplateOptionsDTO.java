package com.brad.pms.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class DevelopmentWorkflowTemplateOptionsDTO {
    private List<WorkflowTemplateSummaryDTO> topicTemplates;
    private List<WorkflowTemplateSummaryDTO> storyTemplates;
}
