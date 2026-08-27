package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_user_position")
public class UserPositionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long orgUnitId;
    private Long positionId;
    private Long managerUserId;
    private String assignmentType;
    private Boolean isPrimary;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
