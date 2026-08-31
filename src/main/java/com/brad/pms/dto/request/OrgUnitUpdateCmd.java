package com.brad.pms.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** Fields that can be changed without changing an organization's identity. */
@Data
public class OrgUnitUpdateCmd {
    @NotBlank(message = "组织名称不能为空")
    private String name;
    private String typeCode;
    private Long leaderUserId;
    /** Set true to explicitly clear an existing leader; omitted means keep it. */
    private Boolean clearLeader;
    private Integer sort;
}
