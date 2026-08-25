package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDTO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private LocalDateTime createdAt;
}
