package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NodeSolutionDecisionDTO {
    private Long id;
    private String result;
    private String conditions;
    private String status;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Integer version;
    private boolean canEdit;
}
