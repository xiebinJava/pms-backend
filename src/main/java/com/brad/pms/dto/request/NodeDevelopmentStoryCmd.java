package com.brad.pms.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class NodeDevelopmentStoryCmd {

    private Long id;

    @NotBlank
    @Size(max = 300)
    private String title;

    private Long ownerId;

    private Long iterationPlanId;

    @Size(max = 20)
    private String status;

    @Min(0)
    @Max(100)
    private Integer progress;

    @Min(0)
    @Max(1000)
    private Integer storyPoints;

    private LocalDate startDate;

    private LocalDate dueDate;

    @Size(max = 500)
    private String blocker;

    @Min(0)
    private Integer sort;
}
