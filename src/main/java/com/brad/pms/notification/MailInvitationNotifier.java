package com.brad.pms.notification;

import com.brad.pms.entity.UserDO;
import com.brad.pms.service.InvitationNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** SMTP adapter for account activation links. The raw token is never logged. */
@Component
@ConditionalOnProperty(prefix = "pms.notification.mail", name = "enabled", havingValue = "true")
public class MailInvitationNotifier implements InvitationNotifier {

    private final JavaMailSender mailSender;
    private final String from;
    private final String baseUrl;

    public MailInvitationNotifier(
            JavaMailSender mailSender,
            @Value("${pms.notification.mail.from:}") String from,
            @Value("${pms.notification.mail.base-url:http://localhost:57979}") String baseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    @Override
    public void send(UserDO user, String activationUrl, LocalDateTime expiresAt) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("用户未配置可用邮箱，无法发送账号邀请");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("PMS 账号激活邀请");
        message.setText("您好，您已被邀请使用 PMS，请通过以下链接设置密码并激活账号：\n"
                + baseUrl + activationUrl + "\n\n链接有效期至：" + expiresAt + "。如非本人操作，请忽略此邮件。");
        mailSender.send(message);
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
