package com.brad.pms.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookPropertiesTest {

    @Test
    void disabledWebhookNeedsNoUrl() {
        WebhookProperties properties = new WebhookProperties();
        assertThatCode(() -> properties.validate("production")).doesNotThrowAnyException();
    }

    @Test
    void productionRequiresHttpsAndALongSecret() {
        WebhookProperties properties = new WebhookProperties();
        properties.setEnabled(true);
        properties.setUrl("http://hooks.example.com/pms");
        properties.setSecret("webhook-secret-16");
        assertThatThrownBy(() -> properties.validate("production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https://");

        properties.setUrl("https://hooks.example.com/pms");
        properties.setSecret("short");
        assertThatThrownBy(() -> properties.validate("development"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16");

        properties.setSecret("webhook-secret-16");
        assertThatCode(() -> properties.validate("production")).doesNotThrowAnyException();
    }
}
