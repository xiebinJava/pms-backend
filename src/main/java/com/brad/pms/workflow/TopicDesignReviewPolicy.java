package com.brad.pms.workflow;

import java.util.List;
import java.util.Map;

/** Completion checks only: incomplete drafts remain saveable. */
public final class TopicDesignReviewPolicy {
    private TopicDesignReviewPolicy() { }
    public static void validate(Map<?, ?> state) {
        if (!text(state.get("productPlanUrl"))) throw new IllegalArgumentException("请填写产品详细方案文档链接");
        for (String key : List.of("productPlanUrl", "uiPlanUrl", "technicalPlanUrl")) {
            Object value = state.get(key);
            if (value == null || "".equals(value) || value instanceof String s && s.isBlank()) continue;
            if (!(value instanceof String)) throw new IllegalArgumentException("方案文档链接格式不正确");
        }
        Object reviewers = state.get("productReviewerIds");
        if (!(reviewers instanceof List<?> list) || list.isEmpty()) throw new IllegalArgumentException("请选择产品评审参与人");
        for (String key : List.of("productReviewerIds", "designReviewerIds", "technicalReviewerIds")) {
            Object value = state.get(key);
            if (value == null) continue;
            if (!(value instanceof List<?> ids) || ids.stream().anyMatch(id -> !(id instanceof Number n)
                    || n.longValue() <= 0 || n.doubleValue() != n.longValue()))
                throw new IllegalArgumentException("评审参与人无效，请重新选择");
        }
        if (!"PASSED".equals(state.get("productReviewStatus"))) throw new IllegalArgumentException("请先完成产品评审");
    }
    private static boolean text(Object value) { return value instanceof String s && !s.isBlank(); }
}
