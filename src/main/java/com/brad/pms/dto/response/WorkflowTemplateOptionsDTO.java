package com.brad.pms.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowTemplateOptionsDTO {
    private List<ProjectTypeDTO> projectTypes;
    private List<WorkflowTemplateSummaryDTO> templates;
}
