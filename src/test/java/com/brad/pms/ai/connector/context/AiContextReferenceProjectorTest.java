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
    private AiContextReadPolicy policy;

    @BeforeEach
    void initIdentity() {
        UserContext.set(new LoginUser(11L, "u", "U"));
        policy = new AiContextReadPolicy(projectMapper, requirementMapper,
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
    void requirementWorkbenchUsesTargetTypeWhenProjectingTargetId() {
        AiContextReadPolicy policy = org.mockito.Mockito.mock(AiContextReadPolicy.class);
        when(policy.canReadReference("topic", 7L)).thenReturn(true);
        AiContextReferenceProjector localProjector = new AiContextReferenceProjector(policy);
        JsonNode content = objectMapper.valueToTree(Map.of(
                "targetType", "TOPIC",
                "targetId", 7L,
                "navigationId", 7L,
                "title", "专题目标"));

        JsonNode projected = localProjector.projectComponentContent(
                "requirement", 23L, "requirement-node-workbench", content);

        assertThat(projected.get("targetType").asText()).isEqualTo("TOPIC");
        assertThat(projected.get("targetId").asLong()).isEqualTo(7L);
        assertThat(projected.get("navigationId").asLong()).isEqualTo(7L);
    }

    @Test
    void projectSnapshotContentFiltersNestedDevelopmentReferences() {
        AiContextReadPolicy policy = org.mockito.Mockito.mock(AiContextReadPolicy.class);
        when(policy.canReadReference("topic", 10L)).thenReturn(true);
        when(policy.canReadReference("story", 20L)).thenReturn(true);
        when(policy.canReadReference("iteration_plan", 30L)).thenReturn(true);
        AiContextReferenceProjector localProjector = new AiContextReferenceProjector(policy);
        JsonNode content = objectMapper.valueToTree(Map.of(
                "topics", List.of(Map.of(
                        "id", 10L,
                        "stories", List.of(
                                Map.of("id", 20L, "iterationPlanId", 30L, "iterationPlanName", "可读迭代"),
                                Map.of("id", 21L, "iterationPlanId", 31L, "iterationPlanName", "不可读迭代")))),
                "summary", Map.of("topicCount", 1, "storyCount", 2)));

        JsonNode projected = localProjector.projectProjectComponentContent("development-control", content);

        assertThat(projected.path("topics")).hasSize(1);
        assertThat(projected.path("topics").get(0).path("stories")).hasSize(1);
        assertThat(projected.path("topics").get(0).path("stories").get(0).path("id").asLong())
                .isEqualTo(20L);
        assertThat(projected.path("topics").get(0).path("stories").get(0).path("iterationPlanId").asLong())
                .isEqualTo(30L);
    }

    @Test
    void malformedKnownComponentContentDoesNotCrashProjection() {
        JsonNode content = objectMapper.createArrayNode().add(1).add(2);

        JsonNode projected = projector.projectComponentContent(
                "story", 8L, "story-node-workbench", content);

        assertThat(projected).isEqualTo(content);
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

    @Test
    void realObjectNodeComponentsAreFilteredExactlyLikeMapFixtures() throws Exception {
        when(authorizationService.has("project:read")).thenReturn(true);
        com.brad.pms.entity.ProjectDO project = new com.brad.pms.entity.ProjectDO();
        project.setId(7L);
        project.setDeleted(false);
        when(projectMapper.selectIncludingDeleted(7L)).thenReturn(project);
        when(projectPermissionService.canReadProject(project)).thenReturn(false);
        // 真实 DTO 形态：fieldValues 的 __components 为 ObjectNode（Map<String,JsonNode> 的 value）。
        com.fasterxml.jackson.databind.node.ObjectNode components =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        com.fasterxml.jackson.databind.node.ObjectNode unknown =
                components.putObject("unknown-workbench");
        unknown.put("topicId", "9");
        unknown.put("payload", "原始内容");
        com.fasterxml.jackson.databind.node.ObjectNode workbench =
                components.putObject("story-node-workbench");
        workbench.put("title", "我的故事");
        workbench.put("topicId", "9");
        workbench.put("baseTopicId", "9");
        workbench.put("projectId", "7");
        workbench.put("descriptionAndAcceptance", "正文 123 不清洗");
        Map<String, Object> fieldValues = new LinkedHashMap<>();
        fieldValues.put("__components", components);
        Map<String, Object> expectedInput = Map.copyOf(fieldValues);

        Map<String, Object> projected = projector.projectFieldValues("story", 8L, fieldValues);

        JsonNode projectedComponents = (JsonNode) projected.get("__components");
        assertThat(projectedComponents.isObject()).isTrue();
        assertThat(projectedComponents.has("unknown-workbench")).isFalse();
        JsonNode filteredWorkbench = projectedComponents.get("story-node-workbench");
        assertThat(filteredWorkbench.has("title")).isTrue();
        assertThat(filteredWorkbench.get("descriptionAndAcceptance").asText()).isEqualTo("正文 123 不清洗");
        assertThat(filteredWorkbench.has("topicId")).isFalse();
        assertThat(filteredWorkbench.has("baseTopicId")).isFalse();
        assertThat(filteredWorkbench.has("projectId")).isFalse();
        // 输入不被修改。
        assertThat(components.has("unknown-workbench")).isTrue();
        assertThat(fieldValues).isEqualTo(expectedInput);
    }

    @Test
    void mapBackedComponentsWithJsonNodeValuesAreProjectedToo() {
        when(authorizationService.has("project:read")).thenReturn(true);
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("story-node-workbench", objectMapper.valueToTree(Map.of(
                "projectId", 7L, "title", "故事")));
        Map<String, Object> fieldValues = new LinkedHashMap<>();
        fieldValues.put("__components", components);

        Map<String, Object> projected = projector.projectFieldValues("story", 8L, fieldValues);

        @SuppressWarnings("unchecked")
        Map<String, Object> projectedComponents = (Map<String, Object>) projected.get("__components");
        @SuppressWarnings("unchecked")
        Map<String, Object> workbench = (Map<String, Object>) projectedComponents.get("story-node-workbench");
        assertThat(workbench).doesNotContainKey("projectId");
        assertThat(workbench).containsEntry("title", "故事");
    }

    @Test
    void topicStoryResourceDetailsProjectParentReferencesDirectly() throws Exception {
        when(authorizationService.has("project:read")).thenReturn(true);
        // project 7 readable; topic 90 unreadable; iteration 404 missing.
        com.brad.pms.entity.ProjectDO readableProject = new com.brad.pms.entity.ProjectDO();
        readableProject.setId(7L);
        readableProject.setDeleted(false);
        when(projectMapper.selectIncludingDeleted(7L)).thenReturn(readableProject);
        when(projectPermissionService.canReadProject(readableProject)).thenReturn(true);
        com.brad.pms.entity.ProjectNodeDevelopmentTopicDO topic =
                new com.brad.pms.entity.ProjectNodeDevelopmentTopicDO();
        topic.setId(90L);
        topic.setProjectId(2L);
        topic.setDeleted(false);
        when(topicMapper.selectById(90L)).thenReturn(topic);
        when(projectMapper.selectById(2L)).thenReturn(null);
        when(iterationPlanMapper.selectById(404L)).thenReturn(null);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("title", "跨端故事");
        details.put("projectId", 7L);
        details.put("projectCode", "PRJ-7");
        details.put("projectName", "可读项目");
        details.put("topicId", 90L);
        details.put("topicTitle", "不可读专题");
        details.put("iterationPlanId", 404L);
        details.put("iterationPlanName", "缺失迭代");
        details.put("developmentStatus", "IN_PROGRESS");

        Map<String, Object> projected =
                projector.projectResourceDetails("story", 8L, details);

        assertThat(projected).containsEntry("projectId", 7L);
        assertThat(projected).containsEntry("projectName", "可读项目");
        assertThat(projected).doesNotContainKeys("topicId", "topicTitle",
                "iterationPlanId", "iterationPlanName");
        // topic 资源类型的详情同样按 projectId 组投影。
        details.remove("topicId");
        details.remove("topicTitle");
        details.remove("iterationPlanId");
        details.remove("iterationPlanName");
        Map<String, Object> topicProjection =
                projector.projectResourceDetails("topic", 90L, details);
        assertThat(topicProjection).containsKeys("projectId", "projectCode");
        org.mockito.Mockito.verify(requirementMapper, org.mockito.Mockito.never()).selectById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void readableExecutionTargetDtoSurvivesAndUnreadableIsDropped() {
        when(authorizationService.has("project:read")).thenReturn(true);
        com.brad.pms.entity.ProjectDO readableProject = new com.brad.pms.entity.ProjectDO();
        readableProject.setId(7L);
        readableProject.setDeleted(false);
        when(projectMapper.selectIncludingDeleted(7L)).thenReturn(readableProject);
        when(projectPermissionService.canReadProject(readableProject)).thenReturn(true);
        com.brad.pms.dto.response.RequirementExecutionTargetDTO readableTarget =
                new com.brad.pms.dto.response.RequirementExecutionTargetDTO();
        readableTarget.setTargetType(com.brad.pms.common.enums.RequirementExecutionTargetType.PROJECT);
        readableTarget.setTargetId(7L);
        readableTarget.setTitle("可读项目");
        Map<String, Object> readableDetails = new LinkedHashMap<>();
        readableDetails.put("executionTarget", readableTarget);

        Map<String, Object> projectedReadable =
                projector.projectResourceDetails("requirement", 23L, readableDetails);

        assertThat(projectedReadable).containsKey("executionTarget");

        com.brad.pms.entity.ProjectDO deletedProject = new com.brad.pms.entity.ProjectDO();
        deletedProject.setId(8L);
        deletedProject.setDeleted(true);
        when(projectMapper.selectIncludingDeleted(8L)).thenReturn(deletedProject);
        com.brad.pms.dto.response.RequirementExecutionTargetDTO deletedTarget =
                new com.brad.pms.dto.response.RequirementExecutionTargetDTO();
        deletedTarget.setTargetType(com.brad.pms.common.enums.RequirementExecutionTargetType.PROJECT);
        deletedTarget.setTargetId(8L);
        Map<String, Object> unreadableDetails = new LinkedHashMap<>();
        unreadableDetails.put("executionTarget", deletedTarget);

        Map<String, Object> projectedUnreadable =
                projector.projectResourceDetails("requirement", 23L, unreadableDetails);

        assertThat(projectedUnreadable).doesNotContainKey("executionTarget");
    }
}
