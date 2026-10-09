package com.brad.pms.ai.connector;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.integration.ai.api.AiCapabilityDTO;
import com.brad.pms.dto.response.ProjectTypeDTO;
import com.brad.pms.dto.response.WorkflowTemplateSummaryDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.UserService;
import com.brad.pms.service.WorkflowTemplateService;
import com.brad.pms.entity.UserDO;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiConnectorCapabilityServiceTest {

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void publishesOnlyTheCommandsAvailableToTheCurrentDelegatedScope() {
        UserContext.set(new LoginUser(7L, "alex", "张伟"));
        WorkflowTemplateService workflowTemplateService = mock(WorkflowTemplateService.class);
        UserService userService = mock(UserService.class);
        ProjectTypeDTO topicType = new ProjectTypeDTO();
        topicType.setId(2L);
        topicType.setCode("topic-management");
        topicType.setName("专题管理");
        ProjectTypeDTO customType = new ProjectTypeDTO();
        customType.setId(9L);
        customType.setCode("custom-management");
        customType.setName("自定义管理");
        WorkflowTemplateSummaryDTO topic = new WorkflowTemplateSummaryDTO();
        topic.setProjectTypeId(2L);
        topic.setName("专题默认流程");
        topic.setPublishedVersionId(88L);
        topic.setPublishedVersionNo(2);
        topic.setDefaultTemplate(true);
        WorkflowTemplateSummaryDTO custom = new WorkflowTemplateSummaryDTO();
        custom.setProjectTypeId(9L);
        custom.setName("自定义流程");
        custom.setPublishedVersionId(99L);
        custom.setPublishedVersionNo(1);
        when(workflowTemplateService.getDefinition(99L)).thenReturn(new WorkflowTemplateDefinition(2, List.of(
                new WorkflowNodeDefinition("custom-node", "自定义节点", null, null, null,
                        List.of("development-control"), List.of(), false, List.of()))));
        when(workflowTemplateService.listProjectTypes()).thenReturn(List.of(topicType, customType));
        when(workflowTemplateService.listTemplates(null, true)).thenReturn(List.of(topic, custom));
        UserDO viewer = new UserDO();
        viewer.setId(7L);
        viewer.setNameZh("张伟");
        when(userService.listByIds(List.of(7L))).thenReturn(List.of(viewer));

        PmsCommandRegistry registry = new PmsCommandRegistry(List.of(command(CommandName.TOPIC_CREATE)));
        AiCapabilityDTO result = new AiConnectorCapabilityService(registry, workflowTemplateService, userService)
                .capabilities();

        assertThat(result.resources()).singleElement().satisfies(resource -> {
            assertThat(resource.type()).isEqualTo("topic");
            assertThat(resource.actions()).singleElement().satisfies(action -> {
                assertThat(action.name()).isEqualTo("topic.create");
                assertThat(action.inputSchema()).containsKey("title");
                assertThat(action.refreshScopes()).contains("development-list");
            });
        });
        assertThat(result.workflowTypes()).hasSize(2);
        assertThat(result.workflowTypes()).extracting(AiCapabilityDTO.WorkflowTypeCapability::processType)
                .containsExactly("topic-management", "custom-management");
        assertThat(result.workflowTypes().get(0).templates()).singleElement().satisfies(version -> {
            assertThat(version.templateVersionId()).isEqualTo(88L);
            assertThat(version.versionNo()).isEqualTo(2);
        });
        AiCapabilityDTO.WorkflowNodeCapability customNode = result.workflowTypes().get(1)
                .templates().get(0).nodes().get(0);
        assertThat(customNode.components()).singleElement()
                .satisfies(component -> assertThat(component.actions()).contains("topic.create"));
        assertThat(result.viewer().id()).isEqualTo(7L);
    }

    private static PmsCommand command(CommandName name) {
        return new PmsCommand() {
            @Override public CommandName name() { return name; }
            @Override public CommandPreview preview(CommandPreviewRequest request) {
                return new CommandPreview(null, name, Instant.now(), request.contextVersion(),
                        List.of(), List.of(Map.of()), List.of());
            }
            @Override public CommandResult execute(AiOperationDO operation) {
                return new CommandResult(operation.getId(), "SUCCEEDED", "ok", Map.of(), List.of());
            }
        };
    }
}
