package com.brad.pms.controller;

import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OrgTreeControllerTest {

    @Test
    void projectOrgTreeIsReadableByProjectUsers() throws Exception {
        Method method = OrgTreeController.class.getDeclaredMethod("tree");
        assertThat(method.getAnnotation(RequirePermission.class).value()).isEqualTo("project:read");
    }

    @Test
    void organizationHistoryIsReadableWithOrganizationPermission() throws Exception {
        Method method = AdminOrgUnitController.class.getDeclaredMethod("history", Long.class);
        assertThat(method.getAnnotation(RequirePermission.class).value()).isEqualTo("admin:org:read");
    }
}
