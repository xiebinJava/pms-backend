package com.brad.pms.notification;

import com.brad.pms.entity.UserDO;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MailNotificationTest {

    @Test
    void sendsInvitationToConfiguredMailboxWithoutLoggingToken() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailInvitationNotifier notifier = new MailInvitationNotifier(sender, "pms@example.com", "https://pms.example.com/");
        UserDO user = new UserDO();
        user.setEmail("brad@example.com");
        LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 30, 12, 0);

        notifier.send(user, "/auth/activate?token=raw-token", expiresAt);

        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("pms@example.com");
        assertThat(message.getTo()).containsExactly("brad@example.com");
        assertThat(message.getText()).contains("https://pms.example.com/auth/activate?token=raw-token");
    }

    @Test
    void refusesUsersWithoutEmail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailPasswordResetNotifier notifier = new MailPasswordResetNotifier(sender, "pms@example.com", "https://pms.example.com");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> notifier.send(new UserDO(), "/reset", LocalDateTime.now())))
                .isInstanceOf(IllegalStateException.class);
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }
}
