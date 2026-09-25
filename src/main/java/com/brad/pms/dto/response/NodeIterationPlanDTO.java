package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class NodeIterationPlanDTO {

    private Long id;
    private String name;
    private Long ownerId;
    private String ownerName;
    private String goal;
    private String status;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer sort;
}
