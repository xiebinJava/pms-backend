package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeedbackHistoryDTO {
    private Long id;
    private Long ticketId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String fromPriority;
    private String toPriority;
    private Long fromAssigneeId;
    private Long toAssigneeId;
    private String note;
    private Long operatorId;
    private String operatorName;
    private String requestId;
    private LocalDateTime createdAt;
}
