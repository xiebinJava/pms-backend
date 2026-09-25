package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_project_member_auto_managed")
public class ProjectMemberAutoManagedDO {
    private Long projectId;
    private Long userId;
    private LocalDateTime createdAt;
}
