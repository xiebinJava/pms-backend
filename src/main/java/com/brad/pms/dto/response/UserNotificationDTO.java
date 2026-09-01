package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserNotificationDTO {

    private Long id;
    private String type;
    private String title;
    private String content;
    private Long projectId;
    private Long taskId;
    private Long actorId;
    private String actorName;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
