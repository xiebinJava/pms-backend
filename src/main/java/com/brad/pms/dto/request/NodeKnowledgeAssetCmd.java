package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeKnowledgeAssetCmd {

    private Long id;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 200)
    private String source;

    @Size(max = 20)
    private String type;

    @Size(max = 500)
    private String improvement;

    @Size(max = 20)
    private String status;

    private Integer sort;
}
