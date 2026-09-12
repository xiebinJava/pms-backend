package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowFieldAttachmentDTO {
    private Long id;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String url;
    private Long createdBy;
    private LocalDateTime createdAt;
}
