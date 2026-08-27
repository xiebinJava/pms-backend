package com.brad.pms.security;

import com.brad.pms.common.enums.SystemRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录用户信息（存于 ThreadLocal，供业务代码随时获取）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private Long id;
    private String username;
    private String nickname;
    private Integer systemRole;

    public LoginUser(Long id, String username, String nickname) {
        this(id, username, nickname, SystemRole.USER.getCode());
    }
}
