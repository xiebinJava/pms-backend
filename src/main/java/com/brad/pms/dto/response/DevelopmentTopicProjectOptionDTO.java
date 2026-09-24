package com.brad.pms.dto.response;

public record DevelopmentTopicProjectOptionDTO(
        Long projectId,
        String projectCode,
        String projectName,
        String nodeKey,
        String nodeName) { }
