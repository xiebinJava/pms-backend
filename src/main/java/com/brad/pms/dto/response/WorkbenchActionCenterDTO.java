package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkbenchActionCenterDTO {

    private int totalCount;
    private int criticalCount;
    private int warningCount;
    private List<ProjectActionItemDTO> items = new ArrayList<>();
}
