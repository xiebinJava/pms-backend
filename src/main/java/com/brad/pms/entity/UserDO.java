package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class UserDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** Optional Chinese display name. */
    private String nameZh;

    /** Locale.ROOT-lower-cased legacy username kept for compatibility. */
    private String usernameNormalized;

    /** Locale.ROOT-lower-cased email used as the canonical account identity. */
    private String emailNormalized;

    private String password;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    /** 系统级角色：0 普通用户，1 管理员。 */
    private Integer systemRole;

    private String status;

    private Integer failedLoginCount;

    private LocalDateTime lockedUntil;

    private LocalDateTime lastLoginAt;

    private LocalDateTime passwordChangedAt;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
