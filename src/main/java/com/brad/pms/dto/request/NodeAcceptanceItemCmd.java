package com.brad.pms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeAcceptanceItemCmd {
    private Long requirementId;
    private String result;
    @Size(max = 1000)
    private String note;
    private Integer sort;
}
