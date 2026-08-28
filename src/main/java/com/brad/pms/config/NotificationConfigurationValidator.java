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
    private final String deploymentEnvironment;
    private final boolean resetTokenExposed;
    private final boolean invitationTokenExposed;
    private final boolean mailEnabled;
    private final String mailHost;
    private final String mailFrom;
    private final int mailPort;
    private final boolean mailSmtpAuth;
    private final boolean mailSmtpStarttlsRequired;
    private final String mailUsername;
    private final String mailPassword;
    private final String publicBaseUrl;

    public NotificationConfigurationValidator(
            ObjectProvider<PasswordResetNotifier> resetNotifiers,
            ObjectProvider<InvitationNotifier> invitationNotifiers,
            @Value("${pms.notification.startup-check-enabled:false}") boolean startupCheckEnabled,
            @Value("${pms.deployment.environment:production}") String deploymentEnvironment,
            @Value("${pms.auth.password-reset-expose-token:false}") boolean resetTokenExposed,
            @Value("${pms.auth.invitation-expose-token:false}") boolean invitationTokenExposed,
            @Value("${pms.notification.mail.enabled:false}") boolean mailEnabled,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${pms.notification.mail.from:}") String mailFrom,
            @Value("${spring.mail.port:587}") int mailPort,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean mailSmtpAuth,
            @Value("${spring.mail.properties.mail.smtp.starttls.required:true}") boolean mailSmtpStarttlsRequired,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${pms.notification.mail.base-url:}") String publicBaseUrl) {
        this.resetNotifiers = resetNotifiers;
        this.invitationNotifiers = invitationNotifiers;
        this.startupCheckEnabled = startupCheckEnabled;
        this.deploymentEnvironment = deploymentEnvironment;
        this.resetTokenExposed = resetTokenExposed;
        this.invitationTokenExposed = invitationTokenExposed;
        this.mailEnabled = mailEnabled;
        this.mailHost = mailHost;
        this.mailFrom = mailFrom;
        this.mailPort = mailPort;
        this.mailSmtpAuth = mailSmtpAuth;
        this.mailSmtpStarttlsRequired = mailSmtpStarttlsRequired;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ("production".equalsIgnoreCase(deploymentEnvironment)
                && (resetTokenExposed || invitationTokenExposed || !startupCheckEnabled)) {
            throw new IllegalStateException("生产环境必须启用通知启动校验并禁止回显密码重置或邀请 token");
        }
        if (!startupCheckEnabled) return;
        if (mailEnabled && (mailHost == null || mailHost.isBlank() || mailFrom == null || mailFrom.isBlank()
                || mailPort < 1 || mailPort > 65535
                || !mailSmtpStarttlsRequired
                || (mailSmtpAuth && (mailUsername == null || mailUsername.isBlank()
                || mailPassword == null || mailPassword.isBlank())))) {
            throw new IllegalStateException("已启用 SMTP 通知，但未配置 PMS_MAIL_HOST 或 PMS_MAIL_FROM");
        }
        if (mailEnabled && !isHttpsUrl(publicBaseUrl)) {
            throw new IllegalStateException("生产 SMTP 通知必须配置 HTTPS 的 PMS_PUBLIC_BASE_URL");
        }
        if (!resetTokenExposed && resetNotifiers.getIfAvailable() == null) {
            throw new IllegalStateException("已启用生产通知检查，但未配置密码重置通知器");
        }
        if (!invitationTokenExposed && invitationNotifiers.getIfAvailable() == null) {
            throw new IllegalStateException("已启用生产通知检查，但未配置账号邀请通知器");
        }
    }

    private boolean isHttpsUrl(String value) {
        return value != null && value.startsWith("https://") && value.length() > "https://".length();
    }
}
