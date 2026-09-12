package com.brad.pms.dto.response;

import com.brad.pms.workflow.WorkflowTemplateDefinition;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowTemplateDTO {
    private Long id;
    private String code;
    private Long projectTypeId;
    private String name;
    private String description;
    private Integer latestVersionNo;
    private Long draftVersionId;
    private Integer draftVersionNo;
    private Integer draftRevision;
    private Long publishedVersionId;
    private Integer publishedVersionNo;
    private WorkflowTemplateDefinition definition;
    private List<String> fixedBlocks;
}
