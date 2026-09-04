package com.brad.pms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeSolutionPackageUpdateCmd {

    private Integer version;

    @Size(max = 20)
    private String packageVersion;

    @Size(max = 4000)
    private String productSolution;

    @Size(max = 4000)
    private String technicalSolution;

    @Size(max = 2000)
    private String summary;

    @Size(max = 2000)
    private String scopeCoverage;

    @Size(max = 2000)
    private String rolloutPremise;
}
