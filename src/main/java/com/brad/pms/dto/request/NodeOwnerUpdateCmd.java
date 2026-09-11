package com.brad.pms.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class NodeOwnerUpdateCmd {

    @NotNull(message = "节点版本不能为空，请刷新后重试")
    private Integer version;

    private Long ownerId;
}
