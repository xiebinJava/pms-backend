package com.brad.pms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeRiskCmd {

    private Long id;

    @Size(max = 300)
    private String title;

    @Size(max = 16)
    private String level;

    private Long ownerId;

    @Size(max = 1000)
    private String response;

    @Size(max = 20)
    private String status;

    private Integer sort;
}
