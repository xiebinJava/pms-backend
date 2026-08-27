package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OrgUnitTreeDTO {
    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private String typeCode;
    private String status;
    private Long leaderUserId;
    private String leaderDisplayName;
    private Integer sort;
    private Integer memberCount;
    private List<OrgUnitTreeDTO> children = new ArrayList<>();
}
