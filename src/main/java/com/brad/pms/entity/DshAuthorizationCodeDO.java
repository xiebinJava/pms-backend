package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_dsh_authorization_codes")
public class DshAuthorizationCodeDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String codeHash;
    private Long userId;
    private Long pmsSessionId;
    private String dshSessionId;
    private String agentId;
    private String scopesJson;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
