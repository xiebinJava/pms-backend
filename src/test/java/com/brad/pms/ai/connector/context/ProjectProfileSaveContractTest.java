package com.brad.pms.ai.connector.context;

import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowFieldType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectProfileSaveContractTest {
    private ProjectDTO project() {
        var project = new ProjectDTO(); project.setId(7L); project.setVersion(3);
        project.setDescription("现有描述"); project.setPriority(2);
        project.setStartDate(LocalDate.of(2026, 10, 1)); project.setEndDate(LocalDate.of(2026, 10, 30));
        return project;
    }
    private ProjectNodeDTO node() {
        var node = new ProjectNodeDTO(); node.setId(21L); node.setProjectId(7L); node.setVersion(0);
        node.setComponents(List.of()); node.setContentOrder(List.of("fields")); node.setFields(List.of(
                new WorkflowFieldDefinition("project-description", "项目描述", WorkflowFieldType.TEXTAREA, true, List.of(), true, "project.description"),
                new WorkflowFieldDefinition("project-schedule", "项目排期", WorkflowFieldType.DATE_RANGE, true, List.of(), true, "project.schedule"),
                new WorkflowFieldDefinition("members", "成员", WorkflowFieldType.PERSON_MULTI, true, List.of(), true, "project.projectMembers"),
                new WorkflowFieldDefinition("hidden-priority", "隐藏优先级", WorkflowFieldType.RADIO, false, List.of(), false, "project.priority")));
        return node;
    }
    @Test void publishesOnlySupportedVisibleBindingsUsingExistingProjectCommand() {
        var contract = ProjectProfileSaveContract.create(project(), node(), List.of("project.update"));
        assertThat(contract).containsEntry("command", "project.update")
                .containsEntry("contractVersion", "project-profile/v1")
                .containsEntry("projectVersion", 3).containsEntry("nodeVersion", 0)
                .containsEntry("fieldValuesPath", "$").containsEntry("completionSupported", false)
                .containsEntry("writableFields", List.of("description", "startDate", "endDate"));
        assertThat(((List<?>)contract.get("protectedFields")).containsAll(List.of("memberIds", "followerIds", "status"))).isTrue();
        assertThat(ProjectProfileSaveContract.values(project(), contract)).containsEntry("description", "现有描述")
                .containsEntry("startDate", LocalDate.of(2026, 10, 1)).doesNotContainKey("priority");
    }
    @Test void unifiedHiddenBindingsAreNotResurrectedByStaleLegacyFields() {
        var node = node(); node.setProjectBasicInfo(true);
        node.setProjectBasicInfoFields(List.of(new com.brad.pms.workflow.WorkflowProjectFieldDefinition(
                "priority", "优先级", true, true)));
        assertThat(ProjectProfileSaveContract.create(project(), node, List.of("project.update")))
                .containsEntry("writableFields", List.of("description", "startDate", "endDate"));
        node.setContentOrder(null);
        assertThat(ProjectProfileSaveContract.create(project(), node, List.of("project.update")))
                .containsEntry("writableFields", List.of("priority"));
    }
    @Test void suppressesProfileSaveForReadOnlyNodeOrClosedProject() {
        var project = project(); var node = node();
        node.setStatus(2);
        assertThat(ProjectProfileSaveContract.create(project, node, List.of("project.update"))).isNull();
        node.setStatus(1); project.setStatus(2);
        assertThat(ProjectProfileSaveContract.create(project, node, List.of("project.update"))).isNull();
        project.setStatus(1); var permissions = new com.brad.pms.dto.response.NodePermissionsDTO();
        permissions.setReadOnly(true); node.setPermissions(permissions);
        assertThat(ProjectProfileSaveContract.create(project, node, List.of("project.update"))).isNull();
    }
    @Test void suppressesContractWithoutAuthorizationVersionOrForComponentWorkbenches() {
        var project = project(); var node = node();
        assertThat(ProjectProfileSaveContract.create(project, node, List.of())).isNull();
        node.setVersion(null);
        assertThat(ProjectProfileSaveContract.create(project, node, List.of("project.update"))).isNull();
        node.setVersion(0); node.setComponents(List.of("solution-design"));
        assertThat(ProjectProfileSaveContract.create(project, node, List.of("project.update"))).isNull();
        node.setComponents(List.of()); project.setVersion(null);
        assertThat(ProjectProfileSaveContract.create(project, node, List.of("project.update"))).isNull();
    }
}
