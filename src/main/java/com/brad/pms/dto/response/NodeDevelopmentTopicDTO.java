package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NodeDevelopmentTopicDTO {

    private Long id;
    private String title;
    private Long ownerId;
    private String ownerName;
    private String latestBuildVersion;
    private String testStatus;
    private String status;
    private Integer progress;
    private String blocker;
    private Integer sort;
    private List<NodeDevelopmentStoryDTO> stories = new ArrayList<>();
}
