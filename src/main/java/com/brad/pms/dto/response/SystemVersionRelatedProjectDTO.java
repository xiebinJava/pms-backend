package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class SystemVersionRelatedProjectDTO {
    private Long id;
    private String code;
    private String name;
    private Integer status;
    private Long ownerId;
    private String ownerName;
    private Integer progress;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<SystemVersionRelatedTopicDTO> topics = new ArrayList<>();
    private List<SystemVersionRelatedStoryDTO> stories = new ArrayList<>();
}
