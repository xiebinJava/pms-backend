package com.brad.pms.config;

import com.brad.pms.service.InvitationNotifier;
import com.brad.pms.service.PasswordResetNotifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Fails fast in production when token delivery is required but not configured. */
@Component
public class NotificationConfigurationValidator implements ApplicationRunner {

    private final ObjectProvider<PasswordResetNotifier> resetNotifiers;
    private final ObjectProvider<InvitationNotifier> invitationNotifiers;
    private final boolean startupCheckEnabled;
    private final boolean resetTokenExposed;
    private final boolean invitationTokenExposed;
    private final boolean mailEnabled;
    private final String mailHost;
    private final String mailFrom;

    public NotificationConfigurationValidator(
            ObjectProvider<PasswordResetNotifier> resetNotifiers,
            ObjectProvider<InvitationNotifier> invitationNotifiers,
            @Value("${pms.notification.startup-check-enabled:false}") boolean startupCheckEnabled,
            @Value("${pms.auth.password-reset-expose-token:false}") boolean resetTokenExposed,
            @Value("${pms.auth.invitation-expose-token:false}") boolean invitationTokenExposed,
            @Value("${pms.notification.mail.enabled:false}") boolean mailEnabled,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${pms.notification.mail.from:}") String mailFrom) {
        this.resetNotifiers = resetNotifiers;
        this.invitationNotifiers = invitationNotifiers;
        this.startupCheckEnabled = startupCheckEnabled;
        this.resetTokenExposed = resetTokenExposed;
        this.invitationTokenExposed = invitationTokenExposed;
        this.mailEnabled = mailEnabled;
        this.mailHost = mailHost;
        this.mailFrom = mailFrom;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!startupCheckEnabled) return;
        if (mailEnabled && (mailHost == null || mailHost.isBlank() || mailFrom == null || mailFrom.isBlank())) {
            throw new IllegalStateException("已启用 SMTP 通知，但未配置 PMS_MAIL_HOST 或 PMS_MAIL_FROM");
        }
        if (!resetTokenExposed && resetNotifiers.getIfAvailable() == null) {
            throw new IllegalStateException("已启用生产通知检查，但未配置密码重置通知器");
        }
        if (!invitationTokenExposed && invitationNotifiers.getIfAvailable() == null) {
            throw new IllegalStateException("已启用生产通知检查，但未配置账号邀请通知器");
        }
    }
}
