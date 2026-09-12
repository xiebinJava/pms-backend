package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectDTO {

    private Long id;
    private Integer version;
    private String code;
    private String name;
    private String description;
    private Integer status;
    private Integer priority;
    private Integer projectLevel;
    private Long projectTypeId;
    private Long workflowTemplateVersionId;
    private Long ownerId;
    private String ownerName;
    private Long createdBy;
    private String createdByName;
    private String createdByAvatar;
    private Long projectManagerId;
    private Long orgUnitId;
    /** 业务线/组织归属名称，列表和详情统一使用同一份后端摘要。 */
    private String orgUnitName;
    /** 包含上级组织的可读路径，例如“公司总部 / 产品制造 BG”。 */
    private String orgUnitPath;
    private String orgUnitLeaderName;
    private String projectManagerName;
    private String projectManagerAvatar;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer progress;
    private Integer taskCount;
    private Integer doneTaskCount;
    private Integer memberCount;
    private String currentNodeKey;
    private String currentNodeName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private ProjectPermissionsDTO permissions;
}
