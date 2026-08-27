package com.brad.pms.service;

import com.brad.pms.entity.UserDO;

import java.time.LocalDateTime;

/** Optional delivery hook for account activation links. */
public interface InvitationNotifier {
    void send(UserDO user, String activationUrl, LocalDateTime expiresAt);
}
