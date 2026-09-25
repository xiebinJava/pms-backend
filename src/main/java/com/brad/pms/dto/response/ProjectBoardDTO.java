package com.brad.pms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Board signals share one server date; absent component data is explicitly null. */
public record ProjectBoardDTO(String asOfDate, String generatedAt, boolean allCompanyScope, List<ProjectRow> projects) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProjectRow(ProjectDTO project, String phase, String health,
                             Integer expectedProgress, Integer progressVariance,
                             int overdueDays, int overdueNodeCount,
                             Integer openRiskCount, Integer highRiskCount, Integer mediumRiskCount,
                             String riskDataState, String nodeDataState, List<String> dataIssues,
                             NextNode nextNode, StorySummary storySummary, AcceptanceSummary acceptanceSummary) { }

    public record NextNode(Long id, String name, String endDate) { }

    public record StorySummary(int total, int notStarted, int inProgress, int testing, int done, int blocked,
                               int overdue, int points, int donePoints) { }

    public record AcceptanceSummary(int total, int open, int resolved) { }
}
