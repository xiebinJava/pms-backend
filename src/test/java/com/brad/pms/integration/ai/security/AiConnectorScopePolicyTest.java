package com.brad.pms.integration.ai.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConnectorScopePolicyTest {

    @Test
    void exposesPmsCliAsTheCurrentConnectorClient() {
        assertThat(AiConnectorScopePolicy.clients()).contains("pms-cli");
        AiConnectorScopePolicy.requireClient("pms-cli");
    }

    @Test
    void rejectsUnknownConnectorClients() {
        assertThatThrownBy(() -> AiConnectorScopePolicy.requireClient("unknown-client"))
                .hasMessageContaining("不受支持");
    }
}
