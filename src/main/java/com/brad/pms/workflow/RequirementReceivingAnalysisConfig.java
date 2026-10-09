package com.brad.pms.workflow;

import com.fasterxml.jackson.databind.JsonNode;

/** Immutable template configuration for the requirement receiving component. */
public record RequirementReceivingAnalysisConfig(
        boolean showFilter,
        boolean showAnalysis,
        boolean showDecision,
        boolean requireCategory,
        boolean showFeasibilityScore,
        boolean requireFeasibilityScore,
        boolean showRoiScore,
        boolean requireRoiScore,
        boolean showStrategicFitScore,
        boolean requireStrategicFitScore,
        boolean requireAnalysisConclusion,
        boolean allowReject) {

    public static RequirementReceivingAnalysisConfig defaults() {
        return new RequirementReceivingAnalysisConfig(false, true, true, true,
                false, false, false, false, true, true, false, true);
    }

    public static RequirementReceivingAnalysisConfig from(JsonNode node) {
        RequirementReceivingAnalysisConfig defaults = defaults();
        if (node == null || !node.isObject()) return defaults;
        return new RequirementReceivingAnalysisConfig(
                defaults.showFilter(), defaults.showAnalysis(), defaults.showDecision(), defaults.requireCategory(),
                defaults.showFeasibilityScore(), defaults.requireFeasibilityScore(),
                defaults.showRoiScore(), defaults.requireRoiScore(),
                defaults.showStrategicFitScore(), defaults.requireStrategicFitScore(),
                defaults.requireAnalysisConclusion(), bool(node, "allowReject", defaults.allowReject()));
    }

    public RequirementReceivingAnalysisConfig withFeasibility(boolean show, boolean required) {
        return new RequirementReceivingAnalysisConfig(showFilter, showAnalysis, showDecision, requireCategory,
                show, required, showRoiScore, requireRoiScore, showStrategicFitScore, requireStrategicFitScore,
                requireAnalysisConclusion, allowReject);
    }

    public RequirementReceivingAnalysisConfig withStrategicFit(boolean show, boolean required) {
        return new RequirementReceivingAnalysisConfig(showFilter, showAnalysis, showDecision, requireCategory,
                showFeasibilityScore, requireFeasibilityScore, showRoiScore, requireRoiScore, show, required,
                requireAnalysisConclusion, allowReject);
    }

    private static boolean bool(JsonNode node, String key, boolean fallback) {
        return node.has(key) ? node.path(key).asBoolean(fallback) : fallback;
    }
}
