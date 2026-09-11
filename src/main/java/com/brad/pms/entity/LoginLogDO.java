package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Authentication audit record. Never store passwords or bearer/refresh tokens here. */
@Data
@TableName("sys_login_log")
public class LoginLogDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String loginName;
    private String result;
    private String reason;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}
