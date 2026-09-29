package com.brad.pms.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RequirementReceivingAnalysisSaveCmd {
    @NotNull(message = "节点版本不能为空")
    private Integer version;

    @NotNull(message = "需求接收分析内容不能为空")
    private JsonNode state;
}
