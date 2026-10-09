package com.brad.pms.workflow;

import java.util.Map;

public final class TopicResearchPolicy {
    private TopicResearchPolicy() { }
    public static void validate(Map<?, ?> state) {
        if ("NO".equals(state.get("needed"))) {
            if (!text(state.get("skipReason"))) throw new IllegalArgumentException("请填写无需调研原因");
        } else if ("YES".equals(state.get("needed"))) {
            if (!text(state.get("goal"))) throw new IllegalArgumentException("请填写调研目标");
            if (!text(state.get("reportUrl"))) throw new IllegalArgumentException("请填写竞品调研报告的文档链接");
        } else throw new IllegalArgumentException("请选择是否需要调研");
    }
    private static boolean text(Object value) { return value instanceof String s && !s.isBlank(); }
}
