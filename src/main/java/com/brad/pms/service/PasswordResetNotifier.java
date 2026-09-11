package com.brad.pms.service;

import com.brad.pms.entity.UserDO;

import java.time.LocalDateTime;

/**
 * Optional delivery hook for password-reset links. Deployments can provide a
 * mail, Feishu, or other notifier bean without coupling the identity service
 * to a particular messaging vendor.
 */
public interface PasswordResetNotifier {
    void send(UserDO user, String resetUrl, LocalDateTime expiresAt);
}
