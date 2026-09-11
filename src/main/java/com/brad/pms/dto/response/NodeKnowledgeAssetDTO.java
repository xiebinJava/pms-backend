package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeKnowledgeAssetDTO {

    private Long id;
    private String name;
    private String source;
    private String type;
    private String improvement;
    private String status;
    private Integer sort;
}
