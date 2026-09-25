package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_project_member_assignment_ref")
public class ProjectMemberAssignmentRefDO {
    private Long projectId;
    private String itemType;
    private Long itemId;
    private String assignmentType;
    private Long assignmentId;
    private Long userId;
    private LocalDateTime createdAt;
}
