package com.brad.pms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NodeReleaseUpdateCmd {

    private Integer version;

    @Size(max = 120)
    private String releaseVersion;

    private LocalDateTime releaseWindowStart;
    private LocalDateTime releaseWindowEnd;

    @Size(max = 32)
    private String releaseType;

    private Boolean packageReady;
    private Boolean configConfirmed;
    private Boolean rollbackReady;
    private Boolean monitoringConfirmed;
    private Boolean onCallConfirmed;

    @Size(max = 32)
    private String decisionResult;

    @Size(max = 2000)
    private String decisionNote;

    @Size(max = 2000)
    private String handoverNotes;

    @Size(max = 2000)
    private String observationItems;

    @Size(max = 500)
    private String emergencyContact;
}
