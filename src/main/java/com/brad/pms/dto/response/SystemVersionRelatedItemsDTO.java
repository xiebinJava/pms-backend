package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Read-only tree projection of the existing iteration-to-development relations. */
@Data
public class SystemVersionRelatedItemsDTO {
    private List<SystemVersionRelatedProjectDTO> projects = new ArrayList<>();
    private List<SystemVersionRelatedTopicDTO> unboundTopics = new ArrayList<>();
    private List<SystemVersionRelatedStoryDTO> unboundStories = new ArrayList<>();
}
