package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_org_unit")
public class OrgUnitDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private Long typeId;
    private String code;
    private String name;
    private Long leaderUserId;
    private Integer sort;
    private String status;
    private String path;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
