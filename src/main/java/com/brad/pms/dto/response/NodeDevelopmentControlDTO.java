package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class NodeDevelopmentControlDTO {

    private Long projectId;
    private Long nodeId;
    private Integer version;
    private String currentIteration;
    private boolean canEdit;
    private LocalDateTime updatedAt;
    private NodeDevelopmentSummaryDTO summary;
    private List<NodeDevelopmentTopicDTO> topics = new ArrayList<>();
}
