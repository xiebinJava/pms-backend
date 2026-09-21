package com.brad.pms.ai.command;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.security.UserContext;

import java.util.List;

/**
 * Command-level delegation-scope guard for the PMS surface.
 *
 * <p>The DSH facade already gates each endpoint by its endpoint scope
 * ({@code pms:command:preview} / {@code pms:command:execute}). This guard adds
 * the per-command half: a PMS delegation token must also hold every scope the
 * command declares (for example {@code pms:workflow:write} for
 * {@code node.complete}), so a narrowed token cannot preview or execute a
 * command the capability catalog would never publish to it.
 *
 * <p>Legacy AI delegation tokens ({@code ai:*}) and ordinary user sessions keep
 * their existing semantics.
 */
public final class PmsCommandScopeGuard {

    private static final String PREVIEW_SCOPE = "pms:command:preview";
    private static final String EXECUTE_SCOPE = "pms:command:execute";

    private PmsCommandScopeGuard() {
    }

    public static void requireScope(CommandName name) {
        if (!UserContext.isDshDelegation()) return;
        if (!UserContext.hasDshScope(PREVIEW_SCOPE) && !UserContext.hasDshScope(EXECUTE_SCOPE)) return;
        List<String> required = PmsCommandMetadata.descriptor(name).scopes();
        boolean missing = required.stream().anyMatch(scope -> !UserContext.hasDshScope(scope));
        if (missing) {
            throw BusinessException.forbidden("没有该命令的权限范围: " + name.code());
        }
    }
}
