package com.brad.pms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthProviderDTO {

    private String type;
    private boolean enabled;
    private String displayName;
    private String startPath;
}
