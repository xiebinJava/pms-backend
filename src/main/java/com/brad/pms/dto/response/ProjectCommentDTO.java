package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectCommentDTO {

    private Long id;
    private Long projectId;
    private Long taskId;
    private String content;
    private Long userId;
    private String userNickname;
    private LocalDateTime createdAt;
}
