package com.brad.pms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WorkflowSystemDefaultDTO {
    private String processTypeCode;
    private String templateCode;
    private Integer versionNo;
    private String fileName;
}
