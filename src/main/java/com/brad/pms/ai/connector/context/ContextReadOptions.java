package com.brad.pms.ai.connector.context;

import com.brad.pms.common.exception.BusinessException;

/**
 * Optional positioning/projection flags for the AI readonly workflow context read.
 * Defaults normalize to the legacy view; only the focused view may target a component.
 */
public record ContextReadOptions(String view, Long nodeId, String componentKey) {

    public static final String VIEW_LEGACY = "legacy";
    public static final String VIEW_FOCUSED = "focused";

    public ContextReadOptions {
        if (view == null || view.isBlank()) view = VIEW_LEGACY;
        switch (view) {
            case VIEW_LEGACY -> view = VIEW_LEGACY;
            case VIEW_FOCUSED -> view = VIEW_FOCUSED;
            default -> throw BusinessException.error("不支持的上下文读取模式: " + view);
        }
        if (nodeId != null && nodeId <= 0) {
            throw BusinessException.error("nodeId 必须为正整数");
        }
        if (componentKey != null) {
            if (componentKey.isBlank()) throw BusinessException.error("componentKey 不能为空白");
            if (!VIEW_FOCUSED.equals(view)) {
                throw BusinessException.error("componentKey 仅允许在 focused 模式下使用");
            }
        }
    }

    public static ContextReadOptions legacy() {
        return new ContextReadOptions(null, null, null);
    }
}
