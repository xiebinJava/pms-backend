package com.brad.pms.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCodeTest {
    @Test
    void projectPermissionFamiliesAreExplicitAndDoNotImplyEachOther() {
        assertThat(PermissionCode.PROJECT_CREATE).isEqualTo("project:create");
        assertThat(PermissionCode.PROJECT_MANAGE).isEqualTo("project:manage");
        assertThat(PermissionCode.PROJECT_COMMENT_WRITE).isEqualTo("project:comment:write");
        assertThat(PermissionCode.isSatisfiedBy(PermissionCode.PROJECT_WRITE, PermissionCode.PROJECT_MANAGE)).isFalse();
        assertThat(PermissionCode.isSatisfiedBy(PermissionCode.PROJECT_COMMENT_WRITE, PermissionCode.PROJECT_WRITE)).isFalse();
        assertThat(PermissionCode.isSatisfiedBy(PermissionCode.AUDIT_READ, PermissionCode.PROJECT_READ)).isFalse();
    }
}
