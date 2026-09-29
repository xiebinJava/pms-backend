package com.brad.pms.workflow;

import java.util.List;

/** Persisted state for the requirement receiving and analysis runtime component. */
public record RequirementReceivingAnalysisState(
        Validity validity,
        List<FilterReason> filterReasons,
        String interpretation,
        String filterNote,
        Category category,
        Integer feasibilityScore,
        Integer roiScore,
        Integer strategicFitScore,
        String analysisConclusion,
        Decision decision,
        String supplementNote,
        String decisionReason) {

    public enum Validity { PENDING, VALID, INVALID, INSUFFICIENT_INFO }
    public enum FilterReason { DUPLICATE, OUT_OF_SCOPE, INSUFFICIENT_INFO, LOW_VALUE, INFEASIBLE, EXISTING_SOLUTION, OTHER }
    public enum Category { FUNCTIONAL, NON_FUNCTIONAL }
    public enum Decision { PASS, NEEDS_INFO, REJECT }
    public enum ValueConclusion { HIGH, MEDIUM, LOW, PENDING }
}
