package com.brad.pms.service;

import com.brad.pms.dto.request.DevelopmentItemNodeUpdateCmd;
import com.brad.pms.entity.DevelopmentItemTaskDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Real MySQL test; every fixture and write rolls back in the isolated test database. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StoryNodeWorkbenchIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DevelopmentItemWorkflowService service;
    @Autowired ObjectMapper mapper;
    @Autowired SqlSessionFactory sqlSessionFactory;
    @Autowired ProjectNodeDevelopmentStoryMapper storyMapper;
    @Autowired DevelopmentItemWorkflowMapper workflowMapper;

    @BeforeEach
    void useApplicationMappings() {
        var assistant = new MapperBuilderAssistant(sqlSessionFactory.getConfiguration(), "story-workbench-integration");
        for (var entity : java.util.List.of(ProjectNodeDevelopmentStoryDO.class, DevelopmentItemWorkflowDO.class,
                DevelopmentItemWorkflowNodeDO.class, DevelopmentItemTaskDO.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @Test
    void savingStoryWorkbenchesKeepsWhitelistHistoryAndOtherNodes() throws Exception {
        Long type = jdbc.queryForObject("SELECT id FROM pms_project_type WHERE code='story-management' AND deleted=FALSE", Long.class);
        jdbc.update("INSERT INTO pms_workflow_template(code,name,project_type_id,latest_version_no) VALUES ('story-workbench-fixture','故事工作台隔离模板',?,1)", type);
        Long template = lastId();
        jdbc.update("INSERT INTO pms_workflow_template_version(template_id,version_no,status,definition_json) VALUES (?,1,'PUBLISHED',?)", template, """
            {"schemaVersion":2,"sourceTopicNodeKey":"custom-node-3","nodes":[
            {"key":"research","name":"需求调研","fields":[],"contentOrder":[]},
            {"key":"develop","name":"开发中","fields":[],"contentOrder":["component:story-node-workbench"],
             "componentConfigs":{"story-node-workbench":{"nodeKey":"develop","variant":"development"}}},
            {"key":"testing","name":"测试中","fields":[],"contentOrder":["component:story-testing"],
             "componentConfigs":{"story-testing":{"testingResultsEnabled":true}}}]}
            """);
        Long version = lastId();
        jdbc.update("INSERT INTO project_node_development_story(title) VALUES ('隔离测试故事')");
        Long story = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow(item_type,item_id,template_version_id) VALUES ('STORY',?,?)", story, version);
        Long workflow = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,sort,status,field_values_json) VALUES (?,'research','需求调研',0,2,?)", workflow, "{\"savedReport\":\"旧调研报告\"}");
        Long research = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,sort,status,field_values_json) VALUES (?,'develop','开发中',1,1,?)", workflow, "{\"__components\":{\"story-node-workbench\":{\"background\":\"旧背景\",\"legacyNote\":\"服务端历史\"}}}");
        Long develop = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,sort,status) VALUES (?,'testing','测试中',2,1)", workflow);
        Long testing = lastId();
        jdbc.update("INSERT INTO pms_development_item_task(workflow_id,node_id,title) VALUES (?,?,'原有任务')", workflow, develop);
        Long task = lastId();

        var developCmd = new DevelopmentItemNodeUpdateCmd();
        developCmd.setVersion(0);
        developCmd.setFieldValues(mapper.readValue("""
            {"__components":{"story-node-workbench":{"implementationNote":"新实现","selfTestResult":"自测通过","unexpected":"丢弃",
            "mergeStatus":"MERGED","deployEnv":"测试环境","testCases":[{"id":"00000000-0000-4000-8000-000000000001","name":"退款","priority":"HIGH","expectedResult":"资金到账"}]}}}
            """, new TypeReference<>() {}));
        service.updateNode(DevelopmentItemType.STORY, story, develop, developCmd);

        var testingCmd = new DevelopmentItemNodeUpdateCmd();
        testingCmd.setVersion(0);
        testingCmd.setFieldValues(mapper.readValue("""
            {"__components":{"story-testing":{"buildVersion":"1.2.3","testStatus":"PASSED","residualIssues":[{"id":"i1","description":"兼容问题"}]}}}
            """, new TypeReference<>() {}));
        service.updateNode(DevelopmentItemType.STORY, story, testing, testingCmd);

        var loaded = service.detail(DevelopmentItemType.STORY, story);
        var developNode = loaded.getNodes().stream().filter(n -> develop.equals(n.getId())).findFirst().orElseThrow();
        var testingNode = loaded.getNodes().stream().filter(n -> testing.equals(n.getId())).findFirst().orElseThrow();
        var workbench = developNode.getFieldValues().get("__components").path("story-node-workbench");
        assertThat(workbench.path("implementationNote").asText()).isEqualTo("新实现");
        assertThat(workbench.path("background").asText()).isEqualTo("旧背景");
        assertThat(workbench.path("legacyNote").asText()).isEqualTo("服务端历史");
        assertThat(workbench.has("unexpected")).isFalse();
        assertThat(workbench.path("testCases").get(0).path("id").asText()).isEqualTo("00000000-0000-4000-8000-000000000001");
        assertThat(workbench.path("testCases").get(0).path("expectedResult").asText()).isEqualTo("资金到账");
        assertThat(workbench.path("deployEnv").asText()).isEqualTo("测试环境");
        assertThat(testingNode.getFieldValues().get("__components").path("story-testing").path("testStatus").asText()).isEqualTo("PASSED");

        assertThat(developNode.getTasks()).hasSize(1);
        assertThat(mapper.readTree(jdbc.queryForObject("SELECT field_values_json FROM pms_development_item_workflow_node WHERE id=?", String.class, research)))
                .isEqualTo(mapper.readTree("{\"savedReport\":\"旧调研报告\"}"));
        assertThat(jdbc.queryForObject("SELECT title FROM pms_development_item_task WHERE id=?", String.class, task)).isEqualTo("原有任务");

        var summaries = service.storyTestingSummaries(java.util.List.of(story));
        assertThat(summaries).containsKey(story);
        assertThat(summaries.get(story).buildVersion()).isEqualTo("1.2.3");
        assertThat(summaries.get(story).testStatus()).isEqualTo("PASSED");
    }

    private Long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }

    @Test
    void writingSavesRealStoryIdentityAndKeepsLegacyContentInAnIsolatedTransaction() throws Exception {
        Long type = jdbc.queryForObject("SELECT id FROM pms_project_type WHERE code='story-management' AND deleted=FALSE", Long.class);
        jdbc.update("INSERT INTO pms_workflow_template(code,name,project_type_id,latest_version_no) VALUES ('writing-fixture','写卡测试',?,1)", type);
        Long template = lastId();
        jdbc.update("INSERT INTO pms_workflow_template_version(template_id,version_no,status,definition_json) VALUES (?,1,'PUBLISHED',?)", template, """
            {"schemaVersion":2,"nodes":[{"key":"write","name":"任意节点名","fields":[],"contentOrder":["component:story-node-workbench"],
            "componentConfigs":{"story-node-workbench":{"nodeKey":"write","variant":"writing"}}}]}
            """);
        Long version = lastId();
        jdbc.update("INSERT INTO project_node_development_story(title) VALUES ('原故事')");
        Long story = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow(item_type,item_id,template_version_id) VALUES ('STORY',?,?)", story, version);
        Long workflow = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,sort,status,field_values_json) VALUES (?,'write','任意节点名',0,1,?)", workflow,
                "{\"__components\":{\"story-node-workbench\":{\"background\":\"旧背景\",\"acceptanceCriteria\":\"旧标准\"}}}");
        Long node = lastId();
        var cmd = new DevelopmentItemNodeUpdateCmd(); cmd.setVersion(0);
        cmd.setFieldValues(mapper.readValue("""
            {"__components":{"story-node-workbench":{"title":"新故事","baseTitle":"原故事","topicId":"","baseTopicId":"",
            "descriptionAndAcceptance":"业务描述及标准","priority":"URGENT"}}}
            """, new TypeReference<>() {}));
        var result = service.updateNode(DevelopmentItemType.STORY, story, node, cmd);
        assertThat(jdbc.queryForObject("SELECT title FROM project_node_development_story WHERE id=?", String.class, story)).isEqualTo("新故事");
        assertThat(result.getTitle()).isEqualTo("新故事");
        var state = result.getNodes().get(0).getFieldValues().get("__components").path("story-node-workbench");
        assertThat(state.path("descriptionAndAcceptance").asText()).isEqualTo("业务描述及标准");
        assertThat(state.path("priority").asText()).isEqualTo("URGENT");
        assertThat(state.path("background").asText()).isEqualTo("旧背景");
        assertThat(state.path("acceptanceCriteria").asText()).isEqualTo("旧标准");
        assertThat(state.has("title")).isFalse();

        // Exercise the actual SQL NULL writes used when detaching a story from its topic,
        // which the service reserves for project-scoped stories (covered by the mock unit test).
        jdbc.update("INSERT INTO project_node_development_topic(title) VALUES ('解绑测试专题')");
        Long topic = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow(item_type,item_id,template_version_id,story_mount_node_key) VALUES ('TOPIC',?,?,'write')", topic, version);
        Long topicWorkflow = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,sort,status) VALUES (?,'write','挂载节点',0,1)", topicWorkflow);
        Long mount = lastId();
        jdbc.update("UPDATE project_node_development_story SET topic_id=?,topic_workflow_node_id=? WHERE id=?", topic, mount, story);
        assertThat(storyMapper.updateScope(story, null, null, null, null)).isEqualTo(1);
        assertThat(workflowMapper.updateScopeFromStory(workflow, null, null, 0)).isEqualTo(1);
        var stored = jdbc.queryForMap("SELECT topic_id,topic_workflow_node_id,iteration_plan_id FROM project_node_development_story WHERE id=?", story);
        assertThat(stored.values()).containsOnlyNulls();
        assertThat(jdbc.queryForMap("SELECT project_id,source_node_id FROM pms_development_item_workflow WHERE id=?", workflow).values()).containsOnlyNulls();

        // A concurrent workflow edit advances the optimistic version and is rejected.
        assertThat(workflowMapper.updateScopeFromStory(workflow, null, null, 0)).isZero();
    }

    @Test
    void iterationStatePersistsPlanLinkAndPeopleAcrossTheRealNodeRoundTrip() throws Exception {
        Long type = jdbc.queryForObject("SELECT id FROM pms_project_type WHERE code='story-management' AND deleted=FALSE", Long.class);
        jdbc.update("INSERT INTO pms_workflow_template(code,name,project_type_id,latest_version_no) VALUES ('iteration-fixture','迭代测试',?,1)", type);
        Long template = lastId();
        jdbc.update("INSERT INTO pms_workflow_template_version(template_id,version_no,status,definition_json) VALUES (?,1,'PUBLISHED',?)", template, """
            {"schemaVersion":2,"nodes":[{"key":"meet","name":"迭代计划会","fields":[],"contentOrder":["component:story-node-workbench"],
            "componentConfigs":{"story-node-workbench":{"nodeKey":"meet","variant":"iteration"}}}]}
            """);
        Long version = lastId();
        jdbc.update("INSERT INTO project_node_development_story(title) VALUES ('迭代故事')");
        Long story = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow(item_type,item_id,template_version_id) VALUES ('STORY',?,?)", story, version);
        Long workflow = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,sort,status,field_values_json) VALUES (?,'meet','迭代计划会',0,1,?)", workflow,
                "{\"__components\":{\"story-node-workbench\":{\"meetingNote\":\"旧结论\"}}}");
        Long node = lastId();

        // Real SQL NULL round-trip for the iteration plan link (project-aware validation is unit-tested).
        assertThat(storyMapper.updateIterationPlan(story, null)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT iteration_plan_id FROM project_node_development_story WHERE id=?", Long.class, story)).isNull();

        var cmd = new DevelopmentItemNodeUpdateCmd(); cmd.setVersion(0);
        cmd.setFieldValues(mapper.readValue("""
            {"__components":{"story-node-workbench":{"iterationPlanId":"","developerIds":[3,5],"testerIds":[7],"meetingNote":"新结论"}}}
            """, new TypeReference<>() {}));
        var result = service.updateNode(DevelopmentItemType.STORY, story, node, cmd);
        var state = result.getNodes().get(0).getFieldValues().get("__components").path("story-node-workbench");
        assertThat(state.path("developerIds")).hasSize(2);
        assertThat(state.path("testerIds")).hasSize(1);
        assertThat(state.path("meetingNote").asText()).isEqualTo("新结论");
    }
}
