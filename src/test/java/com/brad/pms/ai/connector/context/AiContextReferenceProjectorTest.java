package com.brad.pms.ai.connector.context;

import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Filters every system-generated structured reference in AI read payloads. */
@ExtendWith(MockitoExtension.class)
class AiContextReferenceProjectorTest {

    @Mock AuthorizationService authorizationService;
    @Mock com.brad.pms.mapper.ProjectMapper projectMapper;
    @Mock com.brad.pms.mapper.RequirementMapper requirementMapper;
    @Mock com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock com.brad.pms.mapper.ProjectNodeIterationPlanMapper iterationPlanMapper;
    @Mock com.brad.pms.service.ProjectPermissionService projectPermissionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiContextReferenceProjector projector;

    @BeforeEach
    void initIdentity() {
        UserContext.set(new LoginUser(11L, "u", "U"));
        AiContextReadPolicy policy = new AiContextReadPolicy(projectMapper, requirementMapper,
                topicMapper, storyMapper, iterationPlanMapper, authorizationService, projectPermissionService);
        projector = new AiContextReferenceProjector(policy);
    }

    @AfterEach
    void clearIdentity() {
        UserContext.clear();
    }

    @Test
    void anUnreadableExecutionTargetIsDroppedWithNoTargetIdOrTitleLeft() {
        when(authorizationService.has("project:read")).thenReturn(true);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", 23L);
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("targetType", "PROJECT");
        target.put("targetId", 7L);
        target.put("navigationId", 7L);
        target.put("title", "不可读的项目");
        details.put("executionTarget", target);

        Map<String, Object> projected = projector.projectResourceDetails("requirement", 23L, details);

        assertThat(projected).doesNotContainKey("executionTarget");
        assertThat(details).containsKey("executionTarget");
    }

    @Test
    void aReadableExecutionTargetSurvivesUnchanged() {
        when(authorizationService.has("project:read")).thenReturn(true);
        com.brad.pms.entity.ProjectDO readable = readableProject();
        when(projectMapper.selectIncludingDeleted(7L)).thenReturn(readable);
        when(projectPermissionService.canReadProject(readable)).thenReturn(true);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", "ACTIVE");
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("targetType", "PROJECT");
        target.put("targetId", 7L);
        target.put("title", "可读项目");
        details.put("executionTarget", target);
        Map<String, Object> expectedInput = Map.copyOf(details);

        Map<String, Object> projected = projector.projectResourceDetails("requirement", 23L, details);

        assertThat(projected).containsKey("executionTarget");
        assertThat(((Map<?, ?>) projected.get("executionTarget")).get("title")).isEqualTo("可读项目");
        assertThat(details).isEqualTo(expectedInput);
    }

    @Test
    void unreadableSourceRequirementsAreOmittedItemByItem() {
        when(authorizationService.has("requirement:read")).thenReturn(true);
        com.brad.pms.entity.RequirementDO readable = new com.brad.pms.entity.RequirementDO();
        readable.setId(90L);
        readable.setDeleted(false);
        com.brad.pms.entity.RequirementDO unreadable = new com.brad.pms.entity.RequirementDO();
        unreadable.setId(91L);
        unreadable.setDeleted(true);
        when(requirementMapper.selectById(90L)).thenReturn(readable);
        when(requirementMapper.selectById(91L)).thenReturn(unreadable);
        Map<String, Object> sourceOne = new LinkedHashMap<>();
        sourceOne.put("id", 90L);
        sourceOne.put("title", "来源需求A");
        Map<String, Object> sourceTwo = new LinkedHashMap<>();
        sourceTwo.put("id", 91L);
        sourceTwo.put("title", "来源需求B");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sourceRequirements", List.of(sourceOne, sourceTwo));
        details.put("sourceRequirement", sourceTwo);

        Map<String, Object> projected = projector.projectResourceDetails("topic", 7L, details);

        assertThat(projected.get("sourceRequirements") instanceof List<?> list
                ? list.size() : -1).isEqualTo(1);
        assertThat(((Map<?, ?>) ((List<?>) projected.get("sourceRequirements")).get(0)).get("id"))
                .isEqualTo(90L);
        assertThat(projected).doesNotContainKey("sourceRequirement");
        assertThat(details).containsKey("sourceRequirement");
    }

    @Test
    void userBodyTextWithNamesAndNumbersIsNeverRewritten() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("description", "客户联系人写明工单 12345 与目标 7 号系统，含人名张三");

        Map<String, Object> projected = projector.projectResourceDetails("requirement", 23L, details);

        assertThat(projected).containsEntry("description",
                "客户联系人写明工单 12345 与目标 7 号系统，含人名张三");
    }

    @Test
    void legacyUnknownComponentRawContentCannotBypassTheProjection() {
        when(authorizationService.has("project:read")).thenReturn(true);
        Map<String, Object> fieldValues = new LinkedHashMap<>();
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("unknown-workbench", Map.of("topicId", "9", "payload", "原始内容"));
        components.put("story-node-workbench", new LinkedHashMap<>(Map.of(
                "title", "我的故事",
                "topicId", "9",
                "baseTopicId", "9",
                "projectId", "7",
                "descriptionAndAcceptance", "正文 123 不清洗")));
        fieldValues.put("__components", components);
        fieldValues.put("topic.owner", 42);
        Map<String, Object> expectedInput = Map.copyOf(fieldValues);

        Map<String, Object> projected = projector.projectFieldValues("story", 8L, fieldValues);

        @SuppressWarnings("unchecked")
        Map<String, Object> projectedComponents = (Map<String, Object>) projected.get("__components");
        assertThat(projectedComponents).doesNotContainKey("unknown-workbench");
        @SuppressWarnings("unchecked")
        Map<String, Object> workbench = (Map<String, Object>) projectedComponents.get("story-node-workbench");
        assertThat(workbench).containsKey("title").containsKey("descriptionAndAcceptance");
        assertThat(workbench.get("descriptionAndAcceptance")).isEqualTo("正文 123 不清洗");
        assertThat(workbench).doesNotContainKeys("topicId", "baseTopicId", "projectId");
        // Bound scalar values (owner ids) are user-visible facts and stay untouched.
        assertThat(projected).containsEntry("topic.owner", 42);
        assertThat(fieldValues).isEqualTo(expectedInput);
    }

    @Test
    void knownComponentContentIsSanitizedAndUnknownComponentsComeBackNull() {
        when(authorizationService.has("project:read")).thenReturn(true);
        JsonNode content = objectMapper.valueToTree(Map.of(
                "title", "工作台",
                "topicId", "9",
                "targetId", 7,
                "note", "工单 55 写在正文里"));

        JsonNode projected = projector.projectComponentContent("story", 8L, "story-node-workbench", content);

        assertThat(projected.has("topicId")).isFalse();
        assertThat(projected.has("targetId")).isFalse();
        assertThat(projected.get("note").asText()).isEqualTo("工单 55 写在正文里");
        assertThat(projected.get("title").asText()).isEqualTo("工作台");
        assertThat(projector.projectComponentContent("story", 8L, "made-up-component", content)).isNull();
        assertThat(content.has("topicId")).isTrue();
    }

    @Test
    void projectScopedRequirementsAreNotMistakenForStandaloneRequirements() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scopeItems", List.of(Map.of("id", 501L, "title", "项目内范围项")));
        details.put("requirements", List.of(Map.of("id", 502L, "name", "项目内需求")));

        Map<String, Object> projected = projector.projectResourceDetails("project", 7L, details);

        assertThat(projected.get("scopeItems")).isEqualTo(details.get("scopeItems"));
        assertThat(projected.get("requirements")).isEqualTo(details.get("requirements"));
        org.mockito.Mockito.verify(requirementMapper, org.mockito.Mockito.never()).selectById(502L);
    }

    @Test
    void repeatedPermissionResultsAreCachedWithinACallOnly() {
        when(authorizationService.has("requirement:read")).thenReturn(true);
        com.brad.pms.entity.RequirementDO readable = new com.brad.pms.entity.RequirementDO();
        readable.setId(90L);
        readable.setDeleted(false);
        when(requirementMapper.selectById(90L)).thenReturn(readable);
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("sourceRequirement", Map.of("id", 90L, "title", "来源"));
        Map<String, Object> second = new LinkedHashMap<>(first);

        projector.projectResourceDetails("topic", 7L, first);
        projector.projectResourceDetails("topic", 7L, second);

        verify(requirementMapper, org.mockito.Mockito.times(2)).selectById(90L);
    }

    private com.brad.pms.entity.ProjectDO readableProject() {
        com.brad.pms.entity.ProjectDO project = new com.brad.pms.entity.ProjectDO();
        project.setId(7L);
        project.setDeleted(false);
        return project;
    }
}
