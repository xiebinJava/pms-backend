package com.brad.pms.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserPositionCmd {
    private Long orgUnitId;
    private Long positionId;
    private Long managerUserId;
    private String assignmentType;
    private LocalDate startDate;
}
