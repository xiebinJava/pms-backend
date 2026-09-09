package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeRiskDTO {

    private Long id;
    private String title;
    private String level;
    private Long ownerId;
    private String ownerName;
    private String ownerAvatar;
    private String response;
    private String status;
    private Integer sort;
}
