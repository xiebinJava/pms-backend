package com.brad.pms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeSolutionDecisionConfirmCmd {

    private Integer version;

    @Size(max = 32)
    private String result;

    @Size(max = 2000)
    private String conditions;
}
