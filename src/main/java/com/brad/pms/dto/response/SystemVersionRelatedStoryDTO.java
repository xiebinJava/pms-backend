package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class SystemVersionRelatedStoryDTO {
    private Long id;
    private String title;
    private Long projectId;
    private Long topicId;
    private String status;
    private Long ownerId;
    private String ownerName;
    private Integer progress;
    private LocalDate startDate;
    private LocalDate dueDate;
    private List<SystemVersionRelatedIterationDTO> sourceIterations = new ArrayList<>();
}
