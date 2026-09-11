package com.brad.pms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeValueReviewUpdateCmd {

    private Integer version;

    @Size(max = 32)
    private String resultStatus;

    @Size(max = 4000)
    private String actualResult;

    @Size(max = 4000)
    private String retrospectiveConclusion;

    @Size(max = 2000)
    private String followUpActions;
}
