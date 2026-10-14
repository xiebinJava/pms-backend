package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SystemVersionRelatedIterationDTO {
    private Long id;
    private String name;
    private String status;
    private LocalDate startDate;
    private LocalDate dueDate;
}
