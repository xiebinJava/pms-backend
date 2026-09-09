package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NodeSolutionReviewerUpdateCmd {

    @NotNull
    private Long reviewerId;
}
