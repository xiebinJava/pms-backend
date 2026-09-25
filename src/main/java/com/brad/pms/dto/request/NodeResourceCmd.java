package com.brad.pms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NodeResourceCmd {

    private Long id;

    @Size(max = 100)
    private String role;

    private Long ownerId;

    @Size(max = 1000)
    private String focus;

    @Size(max = 20)
    private String status;

    private Integer sort;
}
