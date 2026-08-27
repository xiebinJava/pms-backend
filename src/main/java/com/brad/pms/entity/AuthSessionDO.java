package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_auth_session")
public class AuthSessionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String refreshTokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}
