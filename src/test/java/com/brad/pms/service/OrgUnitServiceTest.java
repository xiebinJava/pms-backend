package com.brad.pms.service;

import com.brad.pms.dto.request.OrgUnitCreateCmd;
import com.brad.pms.dto.request.OrgUnitUpdateCmd;
import com.brad.pms.entity.OrgUnitDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.OrgUnitMapper;
import com.brad.pms.mapper.UserMapper;
import com.brad.pms.mapper.OrgUnitHistoryMapper;
import com.brad.pms.mapper.UserPositionMapper;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrgUnitServiceTest {
    @Autowired OrgUnitService orgUnitService;
    @Autowired OrgUnitMapper orgUnitMapper;
    @Autowired UserMapper userMapper;
    @Autowired OrgUnitHistoryMapper orgUnitHistoryMapper;
    @Autowired UserPositionMapper userPositionMapper;

    @AfterEach
    void clearContext() { UserContext.clear(); }

    @Test
    void updatingOrganizationPersistsLeaderAndDisplayName() {
        UserDO admin = userMapper.findByUsernameNormalized("admin");
        UserContext.set(new LoginUser(admin.getId(), admin.getUsername(), admin.getNickname(), 1));

        OrgUnitDO root = orgUnitMapper.findByCode("HQ");
        OrgUnitCreateCmd create = new OrgUnitCreateCmd();
        create.setParentId(root.getId());
        create.setCode("ORG-UPDATE-TEST");
        create.setName("待更新组织");
        create.setTypeCode("DEPARTMENT");
        OrgUnitDO created = orgUnitMapper.selectById(orgUnitService.create(create).getId());

        OrgUnitUpdateCmd update = new OrgUnitUpdateCmd();
        update.setName("更新后的组织");
        update.setTypeCode("TEAM");
        update.setLeaderUserId(admin.getId());
        update.setSort(12);
        var dto = orgUnitService.update(created.getId(), update);

        assertThat(dto.getName()).isEqualTo("更新后的组织");
        assertThat(dto.getLeaderDisplayName()).isEqualTo("管理员（admin）");
        assertThat(orgUnitMapper.selectById(created.getId()).getLeaderUserId()).isEqualTo(admin.getId());
        assertThat(orgUnitHistoryMapper.findByOrgUnitId(created.getId())).extracting("action")
                .containsExactly("UPDATED", "CREATED");
        assertThat(userPositionMapper.findActivePrimary(admin.getId()).getOrgUnitId())
                .isEqualTo(orgUnitMapper.findByCode("HQ").getId());
    }

    @Test
    void partialOrganizationUpdateKeepsExistingLeaderWhenLeaderIsOmitted() {
        UserDO admin = userMapper.findByUsernameNormalized("admin");
        UserContext.set(new LoginUser(admin.getId(), admin.getUsername(), admin.getNickname(), 1));
        OrgUnitDO root = orgUnitMapper.findByCode("HQ");

        OrgUnitCreateCmd create = new OrgUnitCreateCmd();
        create.setParentId(root.getId());
        create.setCode("ORG-PARTIAL-TEST");
        create.setName("部分更新组织");
        create.setTypeCode("DEPARTMENT");
        create.setLeaderUserId(admin.getId());
        Long id = orgUnitService.create(create).getId();

        OrgUnitUpdateCmd update = new OrgUnitUpdateCmd();
        update.setName("部分更新后的组织");
        var dto = orgUnitService.update(id, update);

        assertThat(dto.getLeaderDisplayName()).isEqualTo("管理员（admin）");
        assertThat(orgUnitMapper.selectById(id).getLeaderUserId()).isEqualTo(admin.getId());
    }
}
