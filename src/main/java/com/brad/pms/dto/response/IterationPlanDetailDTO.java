package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IterationPlanDetailDTO {

    private IterationPlanListDTO plan;
    private List<IterationPlanStoryDTO> stories = new ArrayList<>();
    private List<IterationPlanTaskDTO> tasks = new ArrayList<>();
}
