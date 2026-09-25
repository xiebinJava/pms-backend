package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class NodeKnowledgeStandardDTO {

    private Long projectId;
    private Long nodeId;
    private Integer version;
    private boolean canEdit;
    private LocalDateTime updatedAt;
    private List<NodeKnowledgeAssetDTO> assets = new ArrayList<>();
    private List<NodeKnowledgeActionDTO> actions = new ArrayList<>();
}
