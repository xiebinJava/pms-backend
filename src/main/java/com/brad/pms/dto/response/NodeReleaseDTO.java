package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeReleaseDTO {

    private Long projectId;
    private Long nodeId;
    private Integer version;
    private Long handoverOwnerId;
    private String handoverOwnerName;
    private String handoverOwnerUsername;
    private String handoverNotes;
    private boolean canEdit;
}
