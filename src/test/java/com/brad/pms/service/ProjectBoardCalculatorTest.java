package com.brad.pms.service;

import com.brad.pms.dto.response.ProjectBoardDTO;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectBoardCalculatorTest {
    static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

    @ParameterizedTest
    @CsvSource({"35,CRITICAL", "36,WATCH", "42,WATCH", "43,HEALTHY", "50,HEALTHY"})
    void appliesInclusiveVarianceThresholds(int progress, String health) {
        var result = calculate(project(1, progress), List.of(node(1, TODAY)), List.of());
        assertThat(result.expectedProgress()).isEqualTo(50);
        assertThat(result.progressVariance()).isEqualTo(progress - 50);
        assertThat(result.health()).isEqualTo(health);
    }

    @ParameterizedTest
    @CsvSource({"-11,0", "-10,0", "0,50", "10,100", "11,100"})
    void clampsExpectedProgressAtScheduleBoundaries(int dayOffset, int expected) {
        var result = ProjectBoardCalculator.summarize(project(1, 100), List.of(node(1, TODAY.plusDays(20))),
                List.of(), null, null, TODAY.plusDays(dayOffset));
        assertThat(result.expectedProgress()).isEqualTo(expected);
    }

    @Test
    void roundsExpectedProgressAndDoesNotDivideByZeroOrInventDates() {
        var project = project(1, 0);
        project.setStartDate(TODAY.minusDays(1));
        project.setEndDate(TODAY.plusDays(2));
        assertThat(calculate(project, List.of(node(1, TODAY)), List.of()).expectedProgress()).isEqualTo(33);
        for (LocalDate end : List.of(TODAY.minusDays(1), TODAY.minusDays(2))) {
            project.setEndDate(end);
            var result = calculate(project, List.of(node(1, TODAY)), List.of());
            assertThat(result.expectedProgress()).isNull();
            assertThat(result.progressVariance()).isNull();
            assertThat(result.dataIssues()).contains("PROJECT_DATES_MISSING");
            assertThat(result.health()).isEqualTo("CRITICAL");
        }
    }

    @Test
    void legacyProjectStatusDoesNotDetermineNotStartedPhase() {
        var project = project(0, 50);
        assertThat(calculate(project, List.of(node(0, TODAY)), null).phase()).isEqualTo("NOT_STARTED");
        assertThat(calculate(project, List.of(node(0, TODAY), node(2, TODAY)), null).phase()).isEqualTo("IN_PROGRESS");
        assertThat(calculate(project, List.of(node(1, TODAY)), null).phase()).isEqualTo("IN_PROGRESS");
        assertThat(calculate(project, List.of(), null).phase()).isEqualTo("UNKNOWN");
        assertThat(calculate(project, List.of(node(3, TODAY), node(99, TODAY)), null).phase()).isEqualTo("UNKNOWN");
    }

    @Test
    void yesterdayIsOverdueTodayIsNotAndNextNodeUsesUnfinishedDeadlines() {
        var pending = node(0, TODAY.minusDays(2));
        var active = node(1, TODAY.minusDays(1));
        var dueToday = node(0, TODAY);
        dueToday.setId(30L);
        dueToday.setName("Due today");
        var result = calculate(project(1, 50), List.of(pending, active, dueToday,
                node(2, TODAY.minusDays(9)), node(3, TODAY.minusDays(9)), node(0, TODAY.plusDays(1))), List.of());
        assertThat(result.health()).isEqualTo("CRITICAL");
        assertThat(result.overdueNodeCount()).isEqualTo(2);
        assertThat(result.overdueDays()).isZero();
        assertThat(result.nextNode()).isEqualTo(new ProjectBoardDTO.NextNode(30L, "Due today", TODAY.toString()));
        var project = project(1, 100);
        project.setEndDate(TODAY.minusDays(3));
        assertThat(calculate(project, List.of(dueToday), List.of()).overdueDays()).isEqualTo(3);
        project.setEndDate(TODAY);
        assertThat(calculate(project, List.of(dueToday), List.of()).health()).isEqualTo("HEALTHY");
    }

    @Test
    void knownWarningsSurviveMissingSchedulesAndRiskBaseline() {
        var project = project(1, 50);
        project.setStartDate(null);
        project.setEndDate(null);
        var unknown = calculate(project, List.of(node(1, null)), null);
        assertThat(unknown.health()).isEqualTo("UNKNOWN");
        assertThat(unknown.expectedProgress()).isNull();
        assertThat(unknown.openRiskCount()).isNull();
        assertThat(unknown.dataIssues()).containsExactly("PROJECT_DATES_MISSING", "NODE_SCHEDULE_MISSING", "RISK_BASELINE_NOT_CONFIGURED");
        assertThat(calculate(project, List.of(node(1, TODAY.minusDays(1))), null).health()).isEqualTo("CRITICAL");
        assertThat(calculate(project, List.of(node(1, null)), List.of(risk("OPEN", "HIGH"))).health()).isEqualTo("CRITICAL");
        assertThat(calculate(project, List.of(node(1, null)), List.of(risk("OPEN", "MEDIUM"))).health()).isEqualTo("WATCH");
        assertThat(calculate(project(1, 35), List.of(), null).health()).isEqualTo("UNKNOWN");
    }

    @Test
    void aMissingDeadlineOnAnyUnfinishedNodeKeepsHealthUnderAssessment() {
        var result = calculate(project(1, 50), List.of(node(0, TODAY), node(1, null)), List.of());

        assertThat(result.health()).isEqualTo("UNKNOWN");
        assertThat(result.dataIssues()).contains("NODE_SCHEDULE_MISSING");
    }

    @Test
    void ignoresMissingScheduleOnFutureNodes() {
        var current = node(1, TODAY);
        current.setId(1L);
        var future = node(0, null);
        future.setId(2L);

        var result = calculate(project(1, 50), List.of(current, future), List.of());

        assertThat(result.health()).isEqualTo("HEALTHY");
        assertThat(result.dataIssues()).doesNotContain("NODE_SCHEDULE_MISSING");
    }

    @Test
    void sharedProjectProgressIgnoresDeletedAndInvalidStateNodes() {
        var completed = node(2, TODAY);
        var invalid = node(99, TODAY);
        var deletedCompleted = node(2, TODAY);
        deletedCompleted.setDeleted(true);

        assertThat(ProjectService.calculateNodeProgress(List.of(completed, invalid, deletedCompleted), 50)).isEqualTo(100);
        assertThat(ProjectService.calculateNodeProgress(List.of(), 37)).isEqualTo(37);
    }

    @Test
    void missingWorkflowNodesDoNotReuseLegacyProgressAsNodeCompletion() {
        var project = project(1, 35);

        var result = calculate(project, List.of(), List.of());

        assertThat(result.project().getProgress()).isNull();
        assertThat(project.getProgress()).isEqualTo(35);
        assertThat(result.progressVariance()).isNull();
        assertThat(result.health()).isEqualTo("UNKNOWN");
    }

    @Test
    void countsOnlyOpenRisksAndDistinguishesEmptyFromUnconfigured() {
        var result = calculate(project(1, 50), List.of(node(1, TODAY)), List.of(
                risk("OPEN", "HIGH"), risk("OPEN", "MEDIUM"), risk("OPEN", "LOW"), risk("MITIGATED", "HIGH")));
        assertThat(result.openRiskCount()).isEqualTo(3);
        assertThat(result.highRiskCount()).isEqualTo(1);
        assertThat(result.mediumRiskCount()).isEqualTo(1);
        assertThat(result.riskDataState()).isEqualTo("AVAILABLE");
        assertThat(calculate(project(1, 50), List.of(node(1, TODAY)), List.of()).openRiskCount()).isZero();
        assertThat(calculate(project(1, 50), List.of(node(1, TODAY)), null).riskDataState()).isEqualTo("NOT_CONFIGURED");
        var empty = calculate(project(1, 50), List.of(), null);
        assertThat(empty.nodeDataState()).isEqualTo("NOT_CONFIGURED");
        assertThat(empty.health()).isEqualTo("UNKNOWN");
    }

    @ParameterizedTest
    @CsvSource({"2,COMPLETED", "3,TERMINATED"})
    void terminalLifecycleSuppressesActiveWarnings(int status, String lifecycle) {
        var project = project(status, 0);
        project.setEndDate(TODAY.minusDays(5));
        var result = calculate(project, List.of(node(1, TODAY.minusDays(2))), List.of(risk("OPEN", "HIGH")));
        assertThat(result.phase()).isEqualTo(lifecycle);
        assertThat(result.health()).isEqualTo(lifecycle);
        assertThat(result.overdueDays()).isZero();
        assertThat(result.overdueNodeCount()).isZero();
        assertThat(result.expectedProgress()).isNull();
        assertThat(result.progressVariance()).isNull();
        assertThat(result.nextNode()).isNull();
        assertThat(result.dataIssues()).isEmpty();
    }

    @Test
    void aggregatesStoryStatesPointsDeadlinesAndAcceptanceDomainTerminals() {
        var result = ProjectBoardCalculator.summarize(project(1, 50), List.of(node(1, TODAY)), List.of(),
                List.of(story("NOT_STARTED", null, TODAY.minusDays(1)), story("IN_PROGRESS", 3, TODAY),
                        story("TESTING", 2, TODAY.minusDays(1)), story("DONE", 5, TODAY.minusDays(2)),
                        story("BLOCKED", 8, null)),
                List.of(defect("OPEN"), defect("RESOLVED"), defect("CLOSED"), defect("FIXED")), TODAY);
        assertThat(result.storySummary()).isEqualTo(new ProjectBoardDTO.StorySummary(5, 1, 1, 1, 1, 1, 2, 18, 5));
        assertThat(result.acceptanceSummary()).isEqualTo(new ProjectBoardDTO.AcceptanceSummary(4, 2, 2));
        var configuredEmpty = ProjectBoardCalculator.summarize(project(1, 50), List.of(node(1, TODAY)),
                List.of(), List.of(), List.of(), TODAY);
        assertThat(configuredEmpty.storySummary().total()).isZero();
        assertThat(configuredEmpty.acceptanceSummary().total()).isZero();
    }

    @Test
    void serializesUndefinedSourcesAsExplicitJsonNullEvenUnderNonNullGlobalPolicy() throws Exception {
        var project = project(1, 0);
        project.setStartDate(null);
        var result = calculate(project, List.of(), null);
        var mapper = new ObjectMapper().findAndRegisterModules().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var json = mapper.readTree(mapper.writeValueAsString(result));
        for (String name : List.of("expectedProgress", "progressVariance", "openRiskCount", "highRiskCount", "mediumRiskCount",
                "nextNode", "storySummary", "acceptanceSummary")) {
            assertThat(json.has(name)).as(name).isTrue();
            assertThat(json.get(name).isNull()).as(name).isTrue();
        }
    }

    private static ProjectBoardDTO.ProjectRow calculate(ProjectDTO project, List<ProjectNodeDO> nodes, List<ProjectNodeRiskDO> risks) {
        return ProjectBoardCalculator.summarize(project, nodes, risks, null, null, TODAY);
    }

    static ProjectDTO project(int status, int progress) {
        var project = new ProjectDTO();
        project.setId(1L);
        project.setStatus(status);
        project.setProgress(progress);
        project.setStartDate(TODAY.minusDays(10));
        project.setEndDate(TODAY.plusDays(10));
        return project;
    }

    static ProjectNodeDO node(int status, LocalDate end) {
        var node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey("plan");
        node.setStatus(status);
        node.setEndDate(end);
        return node;
    }

    static ProjectNodeRiskDO risk(String status, String level) {
        var risk = new ProjectNodeRiskDO();
        risk.setProjectId(1L);
        risk.setNodeId(10L);
        risk.setStatus(status);
        risk.setLevel(level);
        return risk;
    }

    static ProjectNodeDevelopmentStoryDO story(String status, Integer points, LocalDate due) {
        var story = new ProjectNodeDevelopmentStoryDO();
        story.setProjectId(1L);
        story.setNodeId(10L);
        story.setStatus(status);
        story.setStoryPoints(points);
        story.setDueDate(due);
        return story;
    }

    static ProjectNodeAcceptanceDefectDO defect(String status) {
        var defect = new ProjectNodeAcceptanceDefectDO();
        defect.setProjectId(1L);
        defect.setNodeId(10L);
        defect.setStatus(status);
        return defect;
    }
}
