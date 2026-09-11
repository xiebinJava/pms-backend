package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class WorkbenchSummaryDTO {

    private int pendingTaskCount;
    private int inProgressTaskCount;
    private int dueSoonTaskCount;
    private int participatingProjectCount;
}
