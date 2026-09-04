package com.brad.pms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NodeRequirementScopeUpdateCmd {

    private Integer version;

    @Size(max = 2000)
    private String objective;

    @Size(max = 2000)
    private String deliverable;

    @Valid
    @Size(max = 100)
    private List<NodeScopeItemCmd> scopeItems = new ArrayList<>();

    @Valid
    @Size(max = 200)
    private List<NodeRequirementCmd> requirements = new ArrayList<>();
}
