package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeScopeItemCmd {

    private Long id;

    @NotBlank
    @Size(max = 8)
    private String direction;

    @NotBlank
    @Size(max = 500)
    private String title;

    private Integer sort;
}
