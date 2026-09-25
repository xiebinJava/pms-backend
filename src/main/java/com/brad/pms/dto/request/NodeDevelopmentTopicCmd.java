package com.brad.pms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NodeDevelopmentTopicCmd {

    private Long id;

    @Size(max = 200)
    private String title;

    private Long ownerId;

    @Size(max = 120)
    private String latestBuildVersion;

    @Size(max = 20)
    private String testStatus;

    private Integer sort;

    @Valid
    @Size(max = 200)
    private List<NodeDevelopmentStoryCmd> stories = new ArrayList<>();
}
