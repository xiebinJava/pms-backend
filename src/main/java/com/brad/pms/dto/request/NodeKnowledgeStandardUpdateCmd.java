package com.brad.pms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NodeKnowledgeStandardUpdateCmd {

    private Integer version;

    @Valid
    @Size(max = 200)
    private List<NodeKnowledgeAssetCmd> assets = new ArrayList<>();

    @Valid
    @Size(max = 200)
    private List<NodeKnowledgeActionCmd> actions = new ArrayList<>();
}
