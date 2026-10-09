package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class NodeIterationPlanDTO {

    private Long id;
    private Long systemId;
    private Long systemVersionId;
    private String systemVersionNo;
    private String systemVersionName;
    private String systemName;
    private String name;
    private Long ownerId;
    private String ownerName;
    private String goal;
    private String status;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer sort;
}
