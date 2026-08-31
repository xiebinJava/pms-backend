package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_permission")
public class PermissionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String resourceType;
    private Long parentId;
    private String httpMethod;
    private String pathPattern;
    private Integer sort;
    private LocalDateTime createdAt;
}
