package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SystemVersionDetailDTO {
    private Long id;
    private Long systemId;
    private String systemName;
    private String versionNo;
    private String versionName;
    private String status;
    private LocalDate plannedReleaseDate;
    private LocalDateTime releasedAt;
    private String releaseNotes;
    private Long ownerId;
    private String ownerName;
    private Integer version;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
