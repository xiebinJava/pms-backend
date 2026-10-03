package com.brad.pms.service;

import com.brad.pms.dto.request.DevelopmentItemNodeUpdateCmd;
import com.brad.pms.entity.DevelopmentItemTaskDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
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
            {"__components":{"story-node-workbench":{"implementationNote":"新实现","selfTestResult":"自测通过","unexpected":"丢弃"}}}
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
}
