package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class FeedbackTicketDTO {
    private Long id;
    private String ticketNo;
    private String title;
    private String content;
    private String feedbackType;
    private String priority;
    private String status;
    private Long projectId;
    private String projectName;
    private Long taskId;
    private Long nodeId;
    private String contextModule;
    private String sourceUrl;
    private Long reporterId;
    private String reporterName;
    private Long assigneeId;
    private String assigneeName;
    private String resolutionNote;
    private String clientRequestId;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
    private List<FeedbackHistoryDTO> history = new ArrayList<>();
}
