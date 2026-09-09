package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class NodePlanResourceRiskDTO {

    private Long projectId;
    private Long nodeId;
    private Integer version;
    private Integer baselineStatus;
    private Long confirmedBy;
    private String confirmedByName;
    private LocalDateTime confirmedAt;
    private Integer sourceDecisionVersion;
    private boolean sourceDecisionChanged;
    private boolean canEdit;
    private List<NodeIterationPlanDTO> iterationPlans = new ArrayList<>();
    private List<NodeResourceDTO> resources = new ArrayList<>();
    private List<NodeRiskDTO> risks = new ArrayList<>();
}
