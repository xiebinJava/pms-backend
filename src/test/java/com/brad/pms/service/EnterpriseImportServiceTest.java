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
import com.brad.pms.mapper.OrgUnitMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EnterpriseImportServiceTest {

    @Autowired
    private EnterpriseImportService importService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserPositionMapper userPositionMapper;

    @Autowired
    private OrgUnitMapper orgUnitMapper;

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
        assertThatCode(() -> importService.commit(preview.getJobId())).doesNotThrowAnyException();
    }

    @Test
    void organizationPreviewResolvesLeaderAndSupportsUtf8Bom() {
        String csv = "\uFEFF组织编码,组织名称,组织类型编码,父组织编码,负责人英文名,排序\n"
                + "IMPORT-BG,导入业务线,bg,HQ,ADMIN,10\n";

        ImportPreviewDTO preview = importService.previewOrganizations(new MockMultipartFile(
                "file", "organizations.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(preview.getErrors()).noneMatch(error -> "负责人英文名".equals(error.getField()));
        importService.commit(preview.getJobId());
        assertThat(orgUnitMapper.findByCode("IMPORT-BG").getLeaderUserId()).isEqualTo(1L);
    }

    @Test
    void oversizedRowCountIsRejectedBeforeCreatingPreviewJob() {
        StringBuilder csv = new StringBuilder("中文名,英文名,邮箱,手机号,主组织编码,岗位编码,角色编码,直属上级英文名\n");
        for (int i = 0; i < 5001; i++) csv.append("导入").append(i).append(",import.row").append(i).append(",row").append(i).append("@example.com,,HQ,EMPLOYEE,MEMBER,\n");

        assertThatThrownBy(() -> importService.previewUsers(new MockMultipartFile(
                "file", "too-many.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8))))
                .hasMessage("单个文件最多导入 5000 行");
    }
}
