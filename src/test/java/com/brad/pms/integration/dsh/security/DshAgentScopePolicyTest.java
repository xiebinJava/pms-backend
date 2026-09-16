package com.brad.pms.integration.dsh.security;

import com.brad.pms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DshAgentScopePolicyTest {

    private final DshAgentScopePolicy policy = new DshAgentScopePolicy();

    @Test
    void projectAssistantUsesOnlyServerAllowedScopesInStableOrder() {
        assertThat(policy.resolve("project_assistant", List.of(
                "pms:task:read", "pms:project:read", "pms:task:read")))
                .containsExactly("pms:project:read", "pms:task:read");
        assertThat(policy.resolve("project_assistant", List.of()))
                .containsExactly("pms:project:read", "pms:task:read", "pms:workspace:embed");
    }

    @Test
    void rejectsUnknownAgentAndScopeEscalation() {
        assertThatThrownBy(() -> policy.resolve("unknown_agent", List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Agent");
        assertThatThrownBy(() -> policy.resolve("project_assistant", List.of("pms:project:write")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限");
    }

    @Test
    void rejectsMalformedAgentId() {
        assertThatThrownBy(() -> policy.resolve("", List.of()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.resolve("project assistant", List.of()))
                .isInstanceOf(BusinessException.class);
    }
}
