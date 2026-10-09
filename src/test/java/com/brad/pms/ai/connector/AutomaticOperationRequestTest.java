package com.brad.pms.ai.connector;

import com.brad.pms.ai.command.CommandName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomaticOperationRequestTest {

    @Test
    void acceptsThePmsCliClientId() {
        AutomaticOperationRequest request = new AutomaticOperationRequest(
                CommandName.PROJECT_CREATE,
                Map.of("name", "订单中心"),
                null,
                null,
                "pms.command",
                "v1",
                "idem-1",
                "pms-cli",
                "req-1");

        assertThat(request.clientId()).isEqualTo("pms-cli");
    }

    @Test
    void rejectsUnknownConnectorClientIds() {
        assertThatThrownBy(() -> new AutomaticOperationRequest(
                CommandName.PROJECT_CREATE,
                Map.of(),
                null,
                null,
                null,
                null,
                "idem-1",
                "unknown-client",
                "req-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pms-cli");
    }
}
