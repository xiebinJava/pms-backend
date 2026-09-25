package com.brad.pms.dto.response;

import lombok.Data;

/** Lightweight, scope-filtered person option for feedback assignment. */
@Data
public class FeedbackAssigneeDTO {
    private Long id;
    private String displayName;
    private String email;
}
