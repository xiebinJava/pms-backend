package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskAttachmentDTO {

    private Long id;
    private Long taskId;
    private Long projectId;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String url;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
}
