package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectReadinessDTO {

    private int completedCount;
    private int totalCount;
    private int percent;
    private int criticalCount;
    private int warningCount;
    private List<ProjectActionItemDTO> items = new ArrayList<>();
    private ProjectActionItemDTO nextAction;
}
