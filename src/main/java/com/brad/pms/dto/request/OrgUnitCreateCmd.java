package com.brad.pms.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class OrgUnitCreateCmd {
    private Long parentId;
    @NotBlank(message = "组织编码不能为空")
    private String code;
    @NotBlank(message = "组织名称不能为空")
    private String name;
    @NotBlank(message = "组织类型不能为空")
    private String typeCode;
    private Long leaderUserId;
    private Integer sort;
}
