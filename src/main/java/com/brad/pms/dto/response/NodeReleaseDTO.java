package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NodeReleaseDTO {

    private Long projectId;
    private Long nodeId;
    private Integer version;
    private String releaseVersion;
    private LocalDateTime releaseWindowStart;
    private LocalDateTime releaseWindowEnd;
    private String releaseType;
    private boolean packageReady;
    private boolean configConfirmed;
    private boolean rollbackReady;
    private boolean monitoringConfirmed;
    private boolean onCallConfirmed;
    private String decisionResult;
    private String decisionNote;
    private String handoverNotes;
    private String observationItems;
    private String emergencyContact;
    private boolean canEdit;
}
