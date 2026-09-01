package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkbenchDTO {

    private WorkbenchSummaryDTO summary = new WorkbenchSummaryDTO();
    private List<WorkbenchTaskDTO> tasks = new ArrayList<>();
    private List<ProjectDTO> projects = new ArrayList<>();
    private List<WorkbenchActivityDTO> activities = new ArrayList<>();
}
