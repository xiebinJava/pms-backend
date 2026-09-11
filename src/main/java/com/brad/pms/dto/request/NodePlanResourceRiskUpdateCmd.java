package com.brad.pms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NodePlanResourceRiskUpdateCmd {

    private Integer version;

    @Valid
    @Size(max = 50)
    private List<NodeResourceCmd> resources = new ArrayList<>();

    @Valid
    @Size(max = 100)
    private List<NodeRiskCmd> risks = new ArrayList<>();

    @Valid
    @Size(max = 50)
    private List<NodeIterationPlanCmd> iterationPlans = new ArrayList<>();
}
