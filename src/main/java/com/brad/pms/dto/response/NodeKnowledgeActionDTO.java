package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class NodeKnowledgeActionDTO {

    private Long id;
    private String title;
    private String note;
    private Long ownerId;
    private String ownerName;
    private LocalDate dueDate;
    private String status;
    private Integer sort;
}
