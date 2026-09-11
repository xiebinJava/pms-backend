package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeAcceptanceItemDTO {
    private Long id;
    private Long requirementId;
    private String requirementCode;
    private String requirementName;
    private String acceptanceCriteria;
    private String result;
    private String note;
    private Integer sort;
}
