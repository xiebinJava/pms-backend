package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class NodeKnowledgeActionCmd {

    private Long id;

    @NotBlank
    @Size(max = 300)
    private String title;

    @Size(max = 500)
    private String note;

    private Long ownerId;
    private LocalDate dueDate;

    @Size(max = 20)
    private String status;

    private Integer sort;
}
