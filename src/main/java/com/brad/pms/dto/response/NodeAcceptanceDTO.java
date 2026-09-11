package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class NodeAcceptanceDTO {
    private Long projectId;
    private Long nodeId;
    private Integer version;
    private Integer status;
    private String result;
    private String residualItems;
    private Long confirmedBy;
    private String confirmedByName;
    private LocalDateTime confirmedAt;
    private Integer sourceBaselineVersion;
    private boolean sourceBaselineChanged;
    private boolean canEdit;
    private List<NodeAcceptanceItemDTO> items = new ArrayList<>();
    private List<NodeAcceptanceDefectDTO> defects = new ArrayList<>();
}
