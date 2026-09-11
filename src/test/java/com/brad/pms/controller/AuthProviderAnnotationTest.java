package com.brad.pms.controller;

import com.brad.pms.security.IgnoreAuth;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AuthProviderAnnotationTest {

    @Test
    void providerEndpointsSkipLogin() throws Exception {
        assertIgnoreAuth("providers");
        assertIgnoreAuth("startOidc");
        assertIgnoreAuth("oidcCallback", com.brad.pms.dto.request.OidcCallbackRequest.class,
                jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class);
        assertIgnoreAuth("ldapLogin", com.brad.pms.dto.request.LoginRequest.class,
                jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class);
    }

    private static void assertIgnoreAuth(String name, Class<?>... parameterTypes) throws Exception {
        Method method = AuthController.class.getDeclaredMethod(name, parameterTypes);
        assertThat(method.getAnnotation(IgnoreAuth.class)).isNotNull();
    }
}
