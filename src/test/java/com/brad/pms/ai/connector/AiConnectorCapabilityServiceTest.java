package com.brad.pms.ai.connector;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.dto.response.DevelopmentWorkflowTemplateOptionsDTO;
import com.brad.pms.integration.ai.api.AiCapabilityDTO;
import com.brad.pms.dto.response.WorkflowTemplateSummaryDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.UserService;
import com.brad.pms.service.WorkflowTemplateService;
import com.brad.pms.entity.UserDO;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
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
        DevelopmentWorkflowTemplateOptionsDTO templates = new DevelopmentWorkflowTemplateOptionsDTO();
        WorkflowTemplateSummaryDTO topic = new WorkflowTemplateSummaryDTO();
        topic.setName("专题默认流程");
        topic.setPublishedVersionId(88L);
        topic.setPublishedVersionNo(2);
        topic.setDefaultTemplate(true);
        templates.setTopicTemplates(List.of(topic));
        templates.setStoryTemplates(List.of());
        templates.setRequirementTemplates(List.of());
        when(workflowTemplateService.developmentOptions()).thenReturn(templates);
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
        assertThat(result.workflowTypes()).singleElement().satisfies(type -> {
            assertThat(type.processType()).isEqualTo("topic-management");
            assertThat(type.templates()).singleElement().satisfies(version -> {
                assertThat(version.templateVersionId()).isEqualTo(88L);
                assertThat(version.versionNo()).isEqualTo(2);
            });
        });
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
