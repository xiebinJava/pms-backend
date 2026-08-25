package com.brad.pms.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MilestoneUpdateCmd {

    private String title;

    private String description;

    private LocalDate dueDate;

    private Integer status;
}
