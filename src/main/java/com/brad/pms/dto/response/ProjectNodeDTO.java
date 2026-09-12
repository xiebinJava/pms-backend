package com.brad.pms.dto.response;

import lombok.Data;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowProjectFieldDefinition;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ProjectNodeDTO {

    private Long id;
    private Integer version;
    private Long projectId;
    private String nodeKey;
    private String name;
    private String description;
    private String deliverable;
    private String roles;
    private Long ownerId;
    private String ownerName;
    private String ownerAvatar;
    private Integer status;
    private Integer sort;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;
    private NodePermissionsDTO permissions;
    private List<String> components;
    private List<WorkflowFieldDefinition> fields;
    private Boolean projectBasicInfo;
    private List<WorkflowProjectFieldDefinition> projectBasicInfoFields;
    private Map<String, JsonNode> fieldValues;
    private Map<String, Integer> fieldValueVersions;
    private Map<String, List<WorkflowFieldAttachmentDTO>> fieldAttachments;
    private List<String> fixedBlocks;
}
