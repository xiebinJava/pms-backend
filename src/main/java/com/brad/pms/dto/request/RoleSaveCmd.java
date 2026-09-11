package com.brad.pms.dto.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RoleSaveCmd {
    private String code;
    private String name;
    private String dataScopeType;
    private Boolean enabled = true;
    private List<String> permissionCodes = new ArrayList<>();
    private List<Long> customOrgUnitIds = new ArrayList<>();
}
