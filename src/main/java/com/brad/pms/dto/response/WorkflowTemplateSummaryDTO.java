package com.brad.pms.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowTemplateSummaryDTO {
    private Long id;
    private String code;
    private Long projectTypeId;
    private String name;
    private String description;
    private Integer draftVersionNo;
    private Integer draftRevision;
    private Integer publishedVersionNo;
    private Long publishedVersionId;
    private List<WorkflowTemplateVersionSummaryDTO> publishedVersions;
    private Long defaultTemplateVersionId;
    private Boolean defaultTemplate;
}
