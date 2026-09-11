package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NodeSolutionDesignDTO {
    private Long projectId;
    private Long nodeId;
    private NodeRequirementBaselineSummaryDTO upstreamBaseline = new NodeRequirementBaselineSummaryDTO();
    private NodeSolutionPackageDTO solutionPackage = new NodeSolutionPackageDTO();
    private List<NodeSolutionReviewDTO> reviews = new ArrayList<>();
    private NodeSolutionDecisionDTO decision = new NodeSolutionDecisionDTO();
}
