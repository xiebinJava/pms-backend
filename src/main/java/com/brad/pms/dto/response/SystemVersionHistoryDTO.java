package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemVersionHistoryDTO {
    private Long id;
    private Long versionId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdAt;
}
