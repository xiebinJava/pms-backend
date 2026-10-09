package com.brad.pms.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequirementReceivingAnalysisPolicyTest {

    @Test
    void calculatesOneDecimalAverageAndValueConclusion() {
        RequirementReceivingAnalysisState state = state(
                RequirementReceivingAnalysisState.Validity.VALID,
                RequirementReceivingAnalysisState.Decision.PASS,
                4, 3, 5, null, null);

        assertThat(RequirementReceivingAnalysisPolicy.averageScore(state)).isEqualTo(4.0);
        assertThat(RequirementReceivingAnalysisPolicy.valueConclusion(state))
                .isEqualTo(RequirementReceivingAnalysisState.ValueConclusion.HIGH);
    }

    @Test
    void incompleteScoresRemainPendingInsteadOfBeingClassified() {
        RequirementReceivingAnalysisState state = state(
                RequirementReceivingAnalysisState.Validity.VALID,
                RequirementReceivingAnalysisState.Decision.PASS,
                4, null, 5, null, null);

        assertThat(RequirementReceivingAnalysisPolicy.averageScore(state)).isNull();
        assertThat(RequirementReceivingAnalysisPolicy.valueConclusion(state))
                .isEqualTo(RequirementReceivingAnalysisState.ValueConclusion.PENDING);
    }

    @Test
    void needsInfoRequiresSupplementNoteAndCannotComplete() {
        RequirementReceivingAnalysisState state = state(
                RequirementReceivingAnalysisState.Validity.INSUFFICIENT_INFO,
                RequirementReceivingAnalysisState.Decision.NEEDS_INFO,
                3, 3, 3, null, "");

        assertThat(RequirementReceivingAnalysisPolicy.validate(state,
                RequirementReceivingAnalysisConfig.defaults(), true))
                .contains("补充说明不能为空", "待补充不能完成需求接收节点");
    }

    @Test
    void draftMayBeSavedBeforeRequiredReceivingFieldsAreComplete() {
        RequirementReceivingAnalysisState draft = new RequirementReceivingAnalysisState(
                RequirementReceivingAnalysisState.Validity.PENDING,
                List.of(), "", "", null, null, null, null, "", null, "", null);

        assertThat(RequirementReceivingAnalysisPolicy.validate(draft,
                RequirementReceivingAnalysisConfig.defaults(), false)).isEmpty();
        assertThat(RequirementReceivingAnalysisPolicy.validate(draft,
                RequirementReceivingAnalysisConfig.defaults(), true))
                .contains("需求分类不能为空", "战略契合度不能为空", "接收结论不能为空")
                .doesNotContain("有效性不能为空", "需求解释不能为空", "可实现性不能为空",
                        "ROI不能为空", "分析结论不能为空");
    }

    @Test
    void defaultsOnlyRequireTheThreeRetainedReceivingFields() {
        RequirementReceivingAnalysisConfig config = RequirementReceivingAnalysisConfig.defaults();

        assertThat(config.showFilter()).isFalse();
        assertThat(config.showAnalysis()).isTrue();
        assertThat(config.showDecision()).isTrue();
        assertThat(config.requireCategory()).isTrue();
        assertThat(config.showFeasibilityScore()).isFalse();
        assertThat(config.showRoiScore()).isFalse();
        assertThat(config.showStrategicFitScore()).isTrue();
        assertThat(config.requireStrategicFitScore()).isTrue();
        assertThat(config.requireAnalysisConclusion()).isFalse();
    }

    @Test
    void hiddenValidityDoesNotBlockTheThreeRetainedFields() {
        RequirementReceivingAnalysisState state = state(
                RequirementReceivingAnalysisState.Validity.INVALID,
                RequirementReceivingAnalysisState.Decision.PASS,
                3, 3, 3, null, null);

        assertThat(RequirementReceivingAnalysisPolicy.validate(state,
                RequirementReceivingAnalysisConfig.defaults(), true))
                .isEmpty();
    }

    @Test
    void hiddenScoreDoesNotParticipateInRequiredValidation() {
        RequirementReceivingAnalysisState state = state(
                RequirementReceivingAnalysisState.Validity.VALID,
                RequirementReceivingAnalysisState.Decision.PASS,
                null, 3, null, null, null);
        RequirementReceivingAnalysisConfig config = RequirementReceivingAnalysisConfig.defaults()
                .withFeasibility(false, true)
                .withStrategicFit(false, true);

        assertThat(RequirementReceivingAnalysisPolicy.validate(state, config, true))
                .doesNotContain("可实现性不能为空", "战略契合度不能为空");
    }

    @Test
    void hiddenFilterReasonDoesNotBlockReceivingCompletion() {
        RequirementReceivingAnalysisState state = state(
                RequirementReceivingAnalysisState.Validity.INVALID,
                RequirementReceivingAnalysisState.Decision.REJECT,
                3, 3, 3,
                List.of(RequirementReceivingAnalysisState.FilterReason.OTHER), "");

        assertThat(RequirementReceivingAnalysisPolicy.validate(state,
                RequirementReceivingAnalysisConfig.defaults(), true))
                .isEmpty();
    }

    private RequirementReceivingAnalysisState state(
            RequirementReceivingAnalysisState.Validity validity,
            RequirementReceivingAnalysisState.Decision decision,
            Integer feasibility, Integer roi, Integer strategicFit,
            Object filterReasonsOrAnalysisConclusion, String supplementNote) {
        @SuppressWarnings("unchecked")
        List<RequirementReceivingAnalysisState.FilterReason> reasons = filterReasonsOrAnalysisConclusion instanceof List<?> list
                ? (List<RequirementReceivingAnalysisState.FilterReason>) list : List.of();
        String analysisConclusion = filterReasonsOrAnalysisConclusion instanceof String text ? text : "分析说明";
        String filterNote = reasons.contains(RequirementReceivingAnalysisState.FilterReason.OTHER) ? "" : "过滤备注";
        return new RequirementReceivingAnalysisState(validity, reasons, "需求解释", filterNote,
                RequirementReceivingAnalysisState.Category.FUNCTIONAL, feasibility, roi, strategicFit,
                analysisConclusion, decision, supplementNote, decision == RequirementReceivingAnalysisState.Decision.REJECT ? "驳回原因" : null);
    }
}
