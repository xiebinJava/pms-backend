package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_org_unit_history")
public class OrgUnitHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orgUnitId;
    private String action;
    private Long operatorId;
    private String beforeJson;
    private String afterJson;
    private String requestId;
    private LocalDateTime createdAt;
}
