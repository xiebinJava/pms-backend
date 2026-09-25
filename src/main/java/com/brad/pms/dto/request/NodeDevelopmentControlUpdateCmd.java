package com.brad.pms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NodeDevelopmentControlUpdateCmd {

    private Integer version;

    @Size(max = 120)
    private String currentIteration;

    @Valid
    @Size(max = 200)
    private List<NodeDevelopmentTopicCmd> topics = new ArrayList<>();
}
