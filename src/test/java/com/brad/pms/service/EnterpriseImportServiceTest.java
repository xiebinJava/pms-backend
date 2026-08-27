package com.brad.pms.service;

import com.brad.pms.dto.response.ImportPreviewDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.brad.pms.mapper.UserMapper;
import com.brad.pms.mapper.UserPositionMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EnterpriseImportServiceTest {

    @Autowired
    private EnterpriseImportService importService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserPositionMapper userPositionMapper;

    @org.junit.jupiter.api.BeforeEach
    void setUserContext() {
        UserContext.set(new LoginUser(1L, "admin", "管理员", 1));
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void previewRejectsCaseInsensitiveDuplicateEnglishNamesWithoutCreatingUsers() {
        String csv = "中文名,英文名,邮箱,手机号,主组织编码,岗位编码,角色编码,直属上级英文名\n"
                + "测试甲,Import.Reviewer,reviewer1@example.com,,HQ,EMPLOYEE,MEMBER,\n"
                + "测试乙,IMPORT.REVIEWER,reviewer2@example.com,,HQ,EMPLOYEE,MEMBER,\n";

        ImportPreviewDTO preview = importService.previewUsers(new MockMultipartFile(
                "file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(preview.getRowCount()).isEqualTo(2);
        assertThat(preview.getErrors()).anyMatch(error -> "英文名".equals(error.getField()) && "批次内重复".equals(error.getMessage()));
    }

    @Test
    void previewAcceptsPhoneAsTheOnlyContact() {
        String csv = "中文名,英文名,邮箱,手机号,主组织编码,岗位编码,角色编码,直属上级英文名\n"
                + "测试丙,Import.Phone,,13800000000,HQ,EMPLOYEE,MEMBER,\n";

        ImportPreviewDTO preview = importService.previewUsers(new MockMultipartFile(
                "file", "users-phone.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(preview.getErrors()).noneMatch(error -> "邮箱/手机号".equals(error.getField()));
    }

    @Test
    void commitPersistsManagerRelationshipAfterAllUsersAreCreated() {
        String csv = "中文名,英文名,邮箱,手机号,主组织编码,岗位编码,角色编码,直属上级英文名\n"
                + "测试丁,Import.ManagerChild,manager-child@example.com,,HQ,EMPLOYEE,MEMBER,ADMIN\n";

        ImportPreviewDTO preview = importService.previewUsers(new MockMultipartFile(
                "file", "users-manager.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(preview.getErrors()).isEmpty();
        importService.commit(preview.getJobId());
        Long userId = userMapper.findByUsernameNormalized("import.managerchild").getId();
        assertThat(userPositionMapper.findActiveByUserId(userId)).anyMatch(position -> Long.valueOf(1L).equals(position.getManagerUserId()));
    }
}
