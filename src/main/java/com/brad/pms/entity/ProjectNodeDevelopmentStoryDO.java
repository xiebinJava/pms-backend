package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@TableName("project_node_development_story")
public class ProjectNodeDevelopmentStoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long nodeId;
    private Long topicId;
    private Long iterationPlanId;
    private String title;
    private Long ownerId;
    private String status;
    private Integer progress;
    private Integer storyPoints;
    private LocalDate startDate;
    private LocalDate dueDate;
    private String blocker;
    private Integer sort;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
