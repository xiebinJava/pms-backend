package com.brad.pms.ai.command;

import com.brad.pms.common.exception.BusinessException;

/** Shared optimistic-concurrency guard for preview/execute commands. */
public final class PmsCommandVersionGuard {

    private PmsCommandVersionGuard() {
    }

    public static void requireMatch(String entity, Object expected, Object actual) {
        if (expected == null || actual == null) return;
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            throw BusinessException.conflict(entity + "已发生变化，请重新生成预览");
        }
    }
}
