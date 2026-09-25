package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeDevelopmentSummaryDTO {

    private int topicCount;
    private int storyCount;
    private int completedStoryCount;
    private int blockedStoryCount;
    private int progress;
}
