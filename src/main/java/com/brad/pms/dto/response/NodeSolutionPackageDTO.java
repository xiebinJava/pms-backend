package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeSolutionPackageDTO {
    private Long id;
    private String productSolution;
    private String technicalSolution;
    private String status;
    private Integer version;
    private boolean canEdit;
}
