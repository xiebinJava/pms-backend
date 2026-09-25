package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeResourceDTO {

    private Long id;
    private String role;
    private Long ownerId;
    private String ownerName;
    private String ownerAvatar;
    private String focus;
    private String status;
    private Integer sort;
}
