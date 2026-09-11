package com.brad.pms.security;

import com.brad.pms.common.enums.SystemRole;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录用户信息（存于 ThreadLocal，供业务代码随时获取）
 */
@Data
@NoArgsConstructor
public class LoginUser {

    private Long id;
    private String username;
    private String nickname;
    private Integer systemRole;
    private String nameZh;
    private String displayName;
    private Long sessionId;

    public LoginUser(Long id, String username, String nickname) {
        this(id, username, nickname, SystemRole.USER.getCode());
    }

    public LoginUser(Long id, String username, String nickname, Integer systemRole) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.systemRole = systemRole;
    }

    public LoginUser(Long id, String username, String nickname, Integer systemRole,
                     String nameZh, String displayName, Long sessionId) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.systemRole = systemRole;
        this.nameZh = nameZh;
        this.displayName = displayName;
        this.sessionId = sessionId;
    }
}
