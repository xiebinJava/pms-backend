package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RoleDTO {
    private Long id;
    private String code;
    private String name;
    private Boolean builtin;
    private String dataScopeType;
    private Boolean enabled;
    private List<String> permissionCodes = new ArrayList<>();
    private List<Long> customOrgUnitIds = new ArrayList<>();
}
