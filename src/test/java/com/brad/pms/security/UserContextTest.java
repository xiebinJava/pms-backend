package com.brad.pms.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class UserContextTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void returnsNullForOptionalPermissionLookupsWhenThereIsNoLogin() {
        assertNull(UserContext.userIdOrNull());
    }
}
