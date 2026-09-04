package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeSolutionPackageDTO {
    private Long id;
    private String packageVersion;
    private String productSolution;
    private String technicalSolution;
    private String summary;
    private String scopeCoverage;
    private String rolloutPremise;
    private String status;
    private Integer version;
    private boolean canEdit;
}
