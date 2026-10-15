package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/** Public, enriched audit representation; never expose the persistence entity directly. */
@Data
public class AuditLogDTO {
    private Long id;
    private Long operatorId;
    private String operatorDisplayName;
    private String action;
    private String resourceType;
    private Long resourceId;
    private Long projectId;
    private String projectName;
    private String beforeJson;
    private String afterJson;
    private String reason;
    private String result;
    private String requestId;
    private LocalDateTime createdAt;
}
