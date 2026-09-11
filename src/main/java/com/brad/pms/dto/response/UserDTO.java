package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDTO {

    private Long id;
    private String username;
    private String nameZh;
    private String displayName;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer systemRole;
    private String status;
    private java.util.List<String> permissionCodes;
    private LocalDateTime createdAt;
}
