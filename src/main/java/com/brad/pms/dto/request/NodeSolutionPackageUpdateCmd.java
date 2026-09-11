package com.brad.pms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeSolutionPackageUpdateCmd {

    private Integer version;

    @Size(max = 4000)
    private String productSolution;

    @Size(max = 4000)
    private String technicalSolution;

}
