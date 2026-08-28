package com.brad.pms.config;

import com.brad.pms.service.InvitationNotifier;
import com.brad.pms.service.PasswordResetNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationConfigurationValidatorTest {

    @Test
    void productionCheckFailsWhenDeliveryIsMissing() {
        ObjectProvider<PasswordResetNotifier> reset = mock(ObjectProvider.class);
        ObjectProvider<InvitationNotifier> invite = mock(ObjectProvider.class);
        when(reset.getIfAvailable()).thenReturn(null);
        when(invite.getIfAvailable()).thenReturn(null);
        NotificationConfigurationValidator validator = new NotificationConfigurationValidator(
                reset, invite, true, "test", false, false, false, "", "", 587, true, true, "", "", "");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .hasMessageContaining("密码重置通知器");
    }

    @Test
    void productionMailCheckRequiresCredentialsAndHttpsBaseUrl() {
        ObjectProvider<PasswordResetNotifier> reset = mock(ObjectProvider.class);
        ObjectProvider<InvitationNotifier> invite = mock(ObjectProvider.class);
        when(reset.getIfAvailable()).thenReturn(mock(PasswordResetNotifier.class));
        when(invite.getIfAvailable()).thenReturn(mock(InvitationNotifier.class));
        NotificationConfigurationValidator validator = new NotificationConfigurationValidator(
                reset, invite, true, "test", false, false, true,
                "smtp.example.com", "pms@example.com", 587, true, true, "", "", "http://localhost:57979");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .hasMessageContaining("SMTP");
    }

    @Test
    void productionRejectsDevelopmentTokenSettingsEvenWhenStartupCheckIsDisabled() {
        ObjectProvider<PasswordResetNotifier> reset = mock(ObjectProvider.class);
        ObjectProvider<InvitationNotifier> invite = mock(ObjectProvider.class);
        NotificationConfigurationValidator validator = new NotificationConfigurationValidator(
                reset, invite, false, "production", true, false, false,
                "", "", 587, true, true, "", "", "");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .hasMessageContaining("生产环境");
    }
}
