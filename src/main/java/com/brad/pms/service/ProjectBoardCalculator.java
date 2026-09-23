package com.brad.pms.service;

import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.dto.response.ProjectBoardDTO;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.ProjectNodeAcceptanceDefectDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeRiskDO;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure calculations over the already scoped, component-filtered snapshot. */
final class ProjectBoardCalculator {
    private ProjectBoardCalculator() { }

    static ProjectBoardDTO.ProjectRow summarize(ProjectDTO project, List<ProjectNodeDO> nodes,
            List<ProjectNodeRiskDO> risks, List<ProjectNodeDevelopmentStoryDO> stories,
            List<ProjectNodeAcceptanceDefectDO> defects, LocalDate today) {
        List<ProjectNodeDO> validNodes = nodes.stream()
                .filter(node -> !Boolean.TRUE.equals(node.getDeleted()))
                .filter(node -> node.getStatus() != null && node.getStatus() >= 0 && node.getStatus() <= 3)
                .toList();
        Integer nodeProgress = validNodes.isEmpty() ? null : project.getProgress();
        String phase = phase(project, validNodes);
        boolean active = ProjectStatus.isOpen(project.getStatus());
        Integer expected = active ? expectedProgress(project, today) : null;
        Integer variance = expected == null || nodeProgress == null ? null : nodeProgress - expected;
        List<ProjectNodeDO> unfinished = validNodes.stream().filter(ProjectBoardCalculator::unfinished).toList();
        int overdueDays = active && before(project.getEndDate(), today)
                ? Math.toIntExact(ChronoUnit.DAYS.between(project.getEndDate(), today)) : 0;
        int overdueNodes = active ? (int) unfinished.stream().filter(node -> before(node.getEndDate(), today)).count() : 0;
        Integer openRisks = risks == null ? null : (int) risks.stream().filter(risk -> "OPEN".equals(risk.getStatus())).count();
        Integer highRisks = riskCount(risks, "HIGH");
        Integer mediumRisks = riskCount(risks, "MEDIUM");
        ProjectNodeDO currentNode = validNodes.stream()
                .filter(node -> node.getStatus() == 1)
                .min(nodeOrder())
                .orElse(null);
        boolean nodeSchedule = !validNodes.isEmpty() && (currentNode == null || currentNode.getEndDate() != null);
        List<String> issues = new ArrayList<>();
        if (active) {
            if (expected == null) issues.add("PROJECT_DATES_MISSING");
            if (!nodeSchedule) issues.add("NODE_SCHEDULE_MISSING");
            if (risks == null) issues.add("RISK_BASELINE_NOT_CONFIGURED");
        }
        String health;
        if ("COMPLETED".equals(phase) || "TERMINATED".equals(phase)) health = phase;
        else if (!active) health = "UNKNOWN";
        else if (overdueDays > 0 || overdueNodes > 0 || positive(highRisks) || (variance != null && variance <= -15)) health = "CRITICAL";
        else if (positive(mediumRisks) || (variance != null && variance <= -8)) health = "WATCH";
        else health = expected != null && nodeSchedule && !validNodes.isEmpty() && risks != null ? "HEALTHY" : "UNKNOWN";

        ProjectDTO boardProject = project;
        if (nodeProgress == null && project.getProgress() != null) {
            boardProject = new ProjectDTO();
            BeanUtils.copyProperties(project, boardProject);
            boardProject.setProgress(null);
        }

        ProjectBoardDTO.NextNode next = active ? unfinished.stream()
                .filter(node -> node.getEndDate() != null && !node.getEndDate().isBefore(today))
                .min(Comparator.comparing(ProjectNodeDO::getEndDate)
                        .thenComparing(ProjectNodeDO::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(node -> new ProjectBoardDTO.NextNode(node.getId(), node.getName(), node.getEndDate().toString()))
                .orElse(null) : null;
        return new ProjectBoardDTO.ProjectRow(boardProject, phase, health, expected, variance, overdueDays, overdueNodes,
                openRisks, highRisks, mediumRisks, risks == null ? "NOT_CONFIGURED" : "AVAILABLE",
                validNodes.isEmpty() ? "NOT_CONFIGURED" : "AVAILABLE", List.copyOf(issues), next,
                storySummary(stories, today), acceptanceSummary(defects));
    }

    private static String phase(ProjectDTO project, List<ProjectNodeDO> nodes) {
        int status = ProjectStatus.normalize(project.getStatus());
        if (status == ProjectStatus.COMPLETED.getCode()) return "COMPLETED";
        if (status == ProjectStatus.TERMINATED.getCode()) return "TERMINATED";
        if (!ProjectStatus.isOpen(status)) return "UNKNOWN";
        if (nodes.stream().anyMatch(node -> node.getStatus() == 1 || node.getStatus() == 2)) return "IN_PROGRESS";
        if (!nodes.isEmpty() && nodes.stream().allMatch(node -> node.getStatus() == 0)) return "NOT_STARTED";
        return "UNKNOWN";
    }

    private static Integer expectedProgress(ProjectDTO project, LocalDate today) {
        LocalDate start = project.getStartDate(), end = project.getEndDate();
        if (start == null || end == null || !end.isAfter(start)) return null;
        double progress = 100.0 * ChronoUnit.DAYS.between(start, today) / ChronoUnit.DAYS.between(start, end);
        return (int) Math.round(Math.max(0, Math.min(100, progress)));
    }

    private static boolean unfinished(ProjectNodeDO node) { return node.getStatus() == 0 || node.getStatus() == 1; }
    private static boolean before(LocalDate date, LocalDate today) { return date != null && date.isBefore(today); }
    private static boolean positive(Integer count) { return count != null && count > 0; }

    private static Comparator<ProjectNodeDO> nodeOrder() {
        return Comparator.comparing(ProjectNodeDO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProjectNodeDO::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Integer riskCount(List<ProjectNodeRiskDO> risks, String level) {
        return risks == null ? null : (int) risks.stream()
                .filter(risk -> "OPEN".equals(risk.getStatus()) && level.equals(risk.getLevel())).count();
    }

    private static ProjectBoardDTO.StorySummary storySummary(List<ProjectNodeDevelopmentStoryDO> stories, LocalDate today) {
        if (stories == null) return null;
        int notStarted = 0, inProgress = 0, testing = 0, done = 0, blocked = 0, overdue = 0, points = 0, donePoints = 0;
        for (ProjectNodeDevelopmentStoryDO story : stories) {
            String status = story.getStatus();
            int value = story.getStoryPoints() == null ? 0 : story.getStoryPoints();
            points += value;
            if ("NOT_STARTED".equals(status)) notStarted++;
            else if ("IN_PROGRESS".equals(status)) inProgress++;
            else if ("TESTING".equals(status)) testing++;
            else if ("DONE".equals(status)) { done++; donePoints += value; }
            else if ("BLOCKED".equals(status)) blocked++;
            if (!"DONE".equals(status) && before(story.getDueDate(), today)) overdue++;
        }
        return new ProjectBoardDTO.StorySummary(stories.size(), notStarted, inProgress, testing, done, blocked, overdue, points, donePoints);
    }

    private static ProjectBoardDTO.AcceptanceSummary acceptanceSummary(List<ProjectNodeAcceptanceDefectDO> defects) {
        if (defects == null) return null;
        // Existing acceptance domain contract: V24__business_acceptance_defect_closure.sql.
        int resolved = (int) defects.stream()
                .filter(defect -> "RESOLVED".equals(defect.getStatus()) || "CLOSED".equals(defect.getStatus())).count();
        return new ProjectBoardDTO.AcceptanceSummary(defects.size(), defects.size() - resolved, resolved);
    }
}
