package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Binds an external OIDC identity to one local PMS account. */
@Data
@TableName("sys_external_identity")
public class ExternalIdentityDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String issuer;
    private String subject;
    private Long userId;
    private String emailSnapshot;
    private String providerType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
