package com.brad.pms.dto.response;

/**
 * Flat, delete-aware task statistics for one workflow node: every non-deleted
 * parent/child task row counts once, so subtasks are never hidden behind their parent.
 */
public record NodeTaskSummaryDTO(long total, long done, long incomplete) {
}
