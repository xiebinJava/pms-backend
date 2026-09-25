package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class NodeRequirementScopeDTO {
    private Long projectId;
    private Long nodeId;
    private Integer version;
    private Integer baselineStatus;
    private Long confirmedBy;
    private String confirmedByName;
    private LocalDateTime confirmedAt;
    private boolean canEdit;
    private List<NodeScopeItemDTO> scopeItems = new ArrayList<>();
    private List<NodeRequirementDTO> requirements = new ArrayList<>();
}
