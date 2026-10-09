package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IterationPlanStatusUpdateCmd {

    @NotBlank
    @Size(max = 20)
    private String status;
}
