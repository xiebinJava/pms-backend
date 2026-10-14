package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SystemVersionRelatedTopicDTO {
    private Long id;
    private String title;
    private Long projectId;
    private String status;
    private Long ownerId;
    private String ownerName;
    private Integer progress;
    private Integer storyCount;
    private List<SystemVersionRelatedStoryDTO> stories = new ArrayList<>();
}
