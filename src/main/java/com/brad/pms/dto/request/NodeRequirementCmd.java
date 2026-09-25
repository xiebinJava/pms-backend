package com.brad.pms.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeRequirementCmd {

    private Long id;

    @Size(max = 32)
    private String code;

    @NotBlank
    @Size(max = 300)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotBlank
    @Size(max = 16)
    private String type;

    @Min(1)
    @Max(3)
    private Integer priority;

    @NotBlank
    @Size(max = 1000)
    private String acceptanceCriteria;

    @Min(0)
    @Max(1)
    private Integer status;

    private Integer sort;
}
