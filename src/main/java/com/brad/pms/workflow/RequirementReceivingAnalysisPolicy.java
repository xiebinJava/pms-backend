package com.brad.pms.workflow;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Shared server-side validation and scoring rules for requirement receiving. */
public final class RequirementReceivingAnalysisPolicy {
    private RequirementReceivingAnalysisPolicy() { }

    public static Double averageScore(RequirementReceivingAnalysisState state) {
        if (state == null || state.feasibilityScore() == null || state.roiScore() == null
                || state.strategicFitScore() == null) return null;
        double average = (state.feasibilityScore() + state.roiScore() + state.strategicFitScore()) / 3.0;
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public static RequirementReceivingAnalysisState.ValueConclusion valueConclusion(
            RequirementReceivingAnalysisState state) {
        Double average = averageScore(state);
        if (average == null) return RequirementReceivingAnalysisState.ValueConclusion.PENDING;
        if (average >= 4.0) return RequirementReceivingAnalysisState.ValueConclusion.HIGH;
        if (average >= 2.5) return RequirementReceivingAnalysisState.ValueConclusion.MEDIUM;
        return RequirementReceivingAnalysisState.ValueConclusion.LOW;
    }

    public static List<String> validate(RequirementReceivingAnalysisState state,
                                        RequirementReceivingAnalysisConfig config,
                                        boolean completing) {
        List<String> errors = new ArrayList<>();
        if (state == null) return List.of("需求接收分析数据不能为空");
        RequirementReceivingAnalysisConfig effective = config == null
                ? RequirementReceivingAnalysisConfig.defaults() : config;

        if (effective.showFilter()) {
            if (completing && (state.validity() == null || state.validity() == RequirementReceivingAnalysisState.Validity.PENDING)) {
                errors.add("有效性不能为空");
            }
            if (completing && !StringUtils.hasText(state.interpretation())) errors.add("需求解释不能为空");
            if (state.filterReasons() != null && state.filterReasons().contains(RequirementReceivingAnalysisState.FilterReason.OTHER)
                    && completing && !StringUtils.hasText(state.filterNote())) {
                errors.add("其他过滤原因必须填写过滤备注");
            }
        }
        if (effective.showAnalysis()) {
            if (completing && effective.requireCategory() && state.category() == null) errors.add("需求分类不能为空");
            validateScore(errors, state.feasibilityScore(), effective.showFeasibilityScore(), effective.requireFeasibilityScore(), completing, "可实现性");
            validateScore(errors, state.roiScore(), effective.showRoiScore(), effective.requireRoiScore(), completing, "ROI");
            validateScore(errors, state.strategicFitScore(), effective.showStrategicFitScore(), effective.requireStrategicFitScore(), completing, "战略契合度");
            if (completing && effective.requireAnalysisConclusion() && !StringUtils.hasText(state.analysisConclusion())) {
                errors.add("分析结论不能为空");
            }
        }
        if (effective.showDecision()) {
            if (completing && state.decision() == null) errors.add("接收结论不能为空");
            if (state.decision() == RequirementReceivingAnalysisState.Decision.NEEDS_INFO) {
                if (completing && !StringUtils.hasText(state.supplementNote())) errors.add("补充说明不能为空");
                if (completing) errors.add("待补充不能完成需求接收节点");
            }
            if (state.decision() == RequirementReceivingAnalysisState.Decision.REJECT
                    && completing && !StringUtils.hasText(state.decisionReason())) errors.add("驳回原因不能为空");
            if (state.decision() == RequirementReceivingAnalysisState.Decision.REJECT && !effective.allowReject()) {
                errors.add("当前模板不允许驳回需求");
            }
            if (completing && effective.showFilter() && state.decision() != null) validateDecisionConsistency(errors, state);
        }
        return errors;
    }

    private static void validateScore(List<String> errors, Integer score, boolean visible, boolean required,
                                      boolean completing, String label) {
        if (!visible) return;
        if (score == null) {
            if (completing && required) errors.add(label + "不能为空");
            return;
        }
        if (score < 1 || score > 5) errors.add(label + "必须是1到5分");
    }

    private static void validateDecisionConsistency(List<String> errors, RequirementReceivingAnalysisState state) {
        if (state.validity() == RequirementReceivingAnalysisState.Validity.INVALID
                && state.decision() != RequirementReceivingAnalysisState.Decision.REJECT) {
            errors.add("无效需求只能驳回");
        }
        if (state.validity() == RequirementReceivingAnalysisState.Validity.INSUFFICIENT_INFO
                && state.decision() != RequirementReceivingAnalysisState.Decision.NEEDS_INFO) {
            errors.add("信息不足需求只能待补充");
        }
        if (state.validity() == RequirementReceivingAnalysisState.Validity.VALID
                && state.decision() != RequirementReceivingAnalysisState.Decision.PASS) {
            errors.add("有效需求只能通过");
        }
        if (state.validity() == RequirementReceivingAnalysisState.Validity.PENDING) {
            errors.add("有效性仍待判断");
        }
    }
}
