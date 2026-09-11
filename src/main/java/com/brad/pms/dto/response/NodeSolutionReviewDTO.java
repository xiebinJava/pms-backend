package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NodeSolutionReviewDTO {
    private Integer version;
    private String reviewType;
    private String status;
    private Long reviewerId;
    private boolean canComplete;
    private String comment;
    private Long completedBy;
    private LocalDateTime completedAt;
}
