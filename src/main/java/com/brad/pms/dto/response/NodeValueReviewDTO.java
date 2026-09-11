package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeValueReviewDTO {

    private Long projectId;
    private Long nodeId;
    private Integer version;
    private String resultStatus;
    private String actualResult;
    private String retrospectiveConclusion;
    private String followUpActions;
    private boolean canEdit;
}
