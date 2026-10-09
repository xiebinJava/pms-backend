package com.brad.pms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeReleaseUpdateCmd {

    private Integer version;

    private Long handoverOwnerId;

    @Size(max = 2000)
    private String handoverNotes;
}
