package com.brad.pms.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class NodeIterationPlanCmd {

    private Long id;

    @Size(max = 200)
    private String name;

    private Long ownerId;

    @Size(max = 500)
    private String goal;

    @Size(max = 20)
    private String status;

    private LocalDate startDate;

    private LocalDate dueDate;

    @Min(0)
    @Max(1000)
    private Integer sort;
}
