package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PersonnelDTO {
    private Long id;
    private String username;
    private String nameZh;
    private String displayName;
    private String email;
    private String phone;
    private String status;
    private String primaryOrgName;
    private Long primaryOrgUnitId;
    private String primaryPositionName;
    private List<String> roles = new ArrayList<>();
    private List<String> partTimeOrgNames = new ArrayList<>();
}
