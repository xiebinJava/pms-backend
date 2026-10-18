package com.brad.pms.ai.connector.context;

import java.util.Map;
import java.util.List;

/** Stable AI-facing metadata describing what a node completion request may confirm. */
public final class CompletionContract {

    public enum Mode {
        AUTO_CONFIRM,
        VALIDATE_ONLY,
        REQUIRES_DOMAIN_CONFIRMATION
    }

    private CompletionContract() { }

    public static void addTo(Map<String, Object> contract, Mode mode, String blockReason) {
        contract.put("completionMode", mode.name());
        contract.put("completionAutomaticConfirmation", mode == Mode.AUTO_CONFIRM);
        if (blockReason != null && !blockReason.isBlank()) {
            contract.put("completionBlockReason", blockReason);
        }
    }

    public static Mode projectMode(String componentKey) {
        return switch (componentKey) {
            case "requirement-scope", "knowledge-standard" -> Mode.AUTO_CONFIRM;
            case "solution-design", "plan-resource-risk", "business-acceptance" ->
                    Mode.REQUIRES_DOMAIN_CONFIRMATION;
            default -> Mode.VALIDATE_ONLY;
        };
    }

    public static String projectBlockReason(String componentKey) {
        return switch (componentKey) {
            case "solution-design" -> "需要方案包提交、三类评审和方案决策完成";
            case "plan-resource-risk" -> "需要计划、资源与风险基线确认";
            case "business-acceptance" -> "需要业务验收与缺陷闭环确认";
            default -> null;
        };
    }

    public static Mode developmentMode(String componentKey) {
        return "topic-design-review".equals(componentKey)
                ? Mode.REQUIRES_DOMAIN_CONFIRMATION : Mode.AUTO_CONFIRM;
    }

    public static Mode developmentMode(List<String> componentKeys) {
        return componentKeys != null && componentKeys.contains("topic-design-review")
                ? Mode.REQUIRES_DOMAIN_CONFIRMATION : Mode.AUTO_CONFIRM;
    }

    public static String developmentBlockReason(String componentKey) {
        return "topic-design-review".equals(componentKey)
                ? "需要产品、设计和技术评审完成" : null;
    }

    public static String developmentBlockReason(List<String> componentKeys) {
        return componentKeys != null && componentKeys.contains("topic-design-review")
                ? "需要产品、设计和技术评审完成" : null;
    }
}
