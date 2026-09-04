package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NodeSolutionReviewDTO {
    private String reviewType;
    private String status;
    private String comment;
    private Long completedBy;
    private LocalDateTime completedAt;
}
