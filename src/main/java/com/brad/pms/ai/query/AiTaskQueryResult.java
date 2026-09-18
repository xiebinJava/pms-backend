package com.brad.pms.ai.query;

import java.time.LocalDate;
import java.util.List;

public record AiTaskQueryResult(
        String dataScope,
        boolean authoritative,
        String timezone,
        LocalDate asOfDate,
        long total,
        long page,
        long pageSize,
        long totalPage,
        Pagination pagination,
        List<TaskItem> tasks) {

    public record Pagination(long page, long pageSize, long totalPage) {
    }

    public record TaskItem(
            Long id,
            Integer version,
            Long projectId,
            String projectCode,
            String projectName,
            Long nodeId,
            String nodeKey,
            String nodeName,
            Long parentId,
            String title,
            Long assigneeId,
            String assigneeName,
            Integer status,
            String statusLabel,
            Integer priority,
            String priorityLabel,
            LocalDate dueDate) {
    }
}
