package com.brad.pms.notification;

import com.brad.pms.entity.UserDO;
import com.brad.pms.service.PasswordResetNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** SMTP adapter for password-reset links. The raw token is never logged. */
@Component
@ConditionalOnProperty(prefix = "pms.notification.mail", name = "enabled", havingValue = "true")
public class MailPasswordResetNotifier implements PasswordResetNotifier {

    private final JavaMailSender mailSender;
    private final String from;
    private final String baseUrl;

    public MailPasswordResetNotifier(
            JavaMailSender mailSender,
            @Value("${pms.notification.mail.from:}") String from,
            @Value("${pms.notification.mail.base-url:http://localhost:57979}") String baseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    @Override
    public void send(UserDO user, String resetUrl, LocalDateTime expiresAt) {
        requireEmail(user);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("PMS 密码重置");
        message.setText("您好，您可以通过以下链接重置 PMS 密码：\n"
                + baseUrl + resetUrl + "\n\n链接有效期至：" + expiresAt + "。如非本人操作，请忽略此邮件。");
        mailSender.send(message);
    }

    private void requireEmail(UserDO user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("用户未配置可用邮箱，无法发送密码重置通知");
        }
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
