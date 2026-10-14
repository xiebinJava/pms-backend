package com.brad.pms.ai.connector.context;

import com.brad.pms.dto.request.RequirementSaveCmd;
import com.brad.pms.dto.response.RequirementListDTO;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.brad.pms.service.ProjectService;
import com.brad.pms.service.RequirementManagementService;
import java.util.List;
import javax.sql.DataSource;

/**
 * Fixture builder for the isolated readonly proof: normal objects are created through
 * business services before tracking opens; abnormal states are written directly to the
 * test container only, never to a user database. All helpers accept the current test
 * container datasource so nothing can point at an arbitrary user database.
 */
public final class AiContextFixtureFactory {

    private final DataSource containerDataSource;

    public AiContextFixtureFactory(DataSource containerDataSource) {
        this.containerDataSource = containerDataSource;
    }

    public DataSource containerDataSource() {
        return containerDataSource;
    }

    public record Fixture(Long projectId, Long requirementId) {
    }

    /**
     * Creates first-slice fixtures through the real business services: one project with
     * default nodes and one requirement. Runs under a fixed admin identity and commits
     * before any tracking window opens.
     */
    public Fixture createLongLivedFixtures(ProjectService projectService,
                                            RequirementManagementService requirementManagementService) {
        UserContext.set(new LoginUser(1L, "admin", "Admin"));
        try {
            com.brad.pms.dto.request.ProjectCreateCmd cmd = new com.brad.pms.dto.request.ProjectCreateCmd();
            cmd.setName("只读上下文验收项目");
            cmd.setDescription("无写验证项目");
            Long projectId = projectService.create(cmd).getId();
            RequirementSaveCmd requirement = new RequirementSaveCmd();
            requirement.setTitle("跨端登录需求");
            requirement.setDescription("验收标准：真实业务描述");
                        Long requirementId = requirementManagementService.create(requirement);
            return new Fixture(projectId, requirementId);
        } finally {
            UserContext.clear();
        }
    }

    /** Direct abnormal fixture (container only, pre-tracking): clear node owners/activate state. */
    public void breakProjectNodeOwnership(Long projectId, List<Long> nodeIds) {
        try (var connection = containerDataSource.getConnection()) {
            for (Long nodeId : nodeIds) {
                try (var statement = connection.prepareStatement(
                        "UPDATE project_node SET owner_id = NULL, status = 0 WHERE id = ?")) {
                    statement.setLong(1, nodeId);
                    statement.executeUpdate();
                }
            }
        } catch (Exception brokenFixture) {
            throw new IllegalStateException("异常 fixture 写入失败", brokenFixture);
        }
    }
}
