package com.brad.pms.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchTaskDTO extends ProjectTaskDTO {

    private String projectName;
    private String projectCode;
}
