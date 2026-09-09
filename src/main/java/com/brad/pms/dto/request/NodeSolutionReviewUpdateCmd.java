package com.brad.pms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeSolutionReviewUpdateCmd {

    @Size(max = 2000)
    private String comment;
}
