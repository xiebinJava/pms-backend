package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class OperationLogDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String beforeJson;
    private String afterJson;
    private String requestId;
    private LocalDateTime createdAt;
}
