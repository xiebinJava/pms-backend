package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeAcceptanceDefectDTO {
    private Long id;
    private Long sourceDefectId;
    private String defectKey;
    private String title;
    private String severity;
    private String status;
    private String impact;
}
