package com.brad.pms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NodeAcceptanceUpdateCmd {
    private Integer version;
    private String result;
    @Size(max = 2000)
    private String residualItems;

    @Valid
    @Size(max = 500)
    private List<NodeAcceptanceItemCmd> items = new ArrayList<>();
}
