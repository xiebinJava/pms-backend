package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.brad.pms.common.enums.TaskScheduleChangeType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project_task_schedule_history")
public class ProjectTaskScheduleHistoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long taskId;

    private LocalDate previousDueDate;

    private LocalDate nextDueDate;

    private TaskScheduleChangeType changeType;

    private Long operatorId;

    private LocalDateTime createdAt;
}
