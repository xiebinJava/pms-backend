package com.brad.pms.service;

import com.brad.pms.dto.request.DevelopmentItemNodeUpdateCmd;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.DevelopmentItemTaskDO;
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
class TopicDevelopmentTestingIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DevelopmentItemWorkflowService service;
    @Autowired ObjectMapper mapper;
    @Autowired SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void useApplicationMappings() {
        // Mock-based tests can replace the shared lambda cache with camel-case columns.
        var assistant = new MapperBuilderAssistant(sqlSessionFactory.getConfiguration(), "topic-testing-integration");
        for (var entity : java.util.List.of(ProjectNodeDevelopmentStoryDO.class, ProjectNodeDevelopmentTopicDO.class,
                DevelopmentItemWorkflowDO.class, DevelopmentItemWorkflowNodeDO.class, DevelopmentItemTaskDO.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @Test
    void savingTestingResultsRoundTripsWithoutReplacingNodesStoriesOrTasks() throws Exception {
        Long type = jdbc.queryForObject("SELECT id FROM pms_project_type WHERE code='topic-management' AND deleted=FALSE", Long.class);
        jdbc.update("INSERT INTO pms_workflow_template(code,name,project_type_id,latest_version_no) VALUES ('topic-testing-fixture','测试工作台隔离模板',?,1)", type);
        Long template = lastId();
        jdbc.update("INSERT INTO pms_workflow_template_version(template_id,version_no,status,definition_json) VALUES (?,1,'PUBLISHED',?)", template, """
            {"schemaVersion":2,"nodes":[
            {"key":"research","name":"需求调研","fields":[],"contentOrder":[]},
            {"key":"develop","name":"开发与测试","fields":[],"contentOrder":["component:story-list"],"componentConfigs":{"story-list":{"testingResultsEnabled":true}}}]}
            """);
        Long version = lastId();
        jdbc.update("INSERT INTO project_node_development_topic(title) VALUES ('隔离测试专题')");
        Long topic = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow(item_type,item_id,template_version_id,story_mount_node_key) VALUES ('TOPIC',?,?,'develop')", topic, version);
        Long workflow = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,sort,status,field_values_json) VALUES (?,'research','需求调研',0,2,?)", workflow, "{\"savedReport\":\"旧调研报告\"}");
        Long research = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,sort,status) VALUES (?,'develop','开发与测试',1,1)", workflow);
        Long node = lastId();
        jdbc.update("INSERT INTO project_node_development_story(topic_id,topic_workflow_node_id,title,progress) VALUES (?,?,'原有故事',37)", topic, node);
        Long story = lastId();
        jdbc.update("INSERT INTO pms_development_item_task(workflow_id,node_id,title) VALUES (?,?,'原有任务')", workflow, node);
        Long task = lastId();

        var cmd = new DevelopmentItemNodeUpdateCmd();
        cmd.setVersion(0);
        cmd.setFieldValues(mapper.readValue("""
            {"__components":{"story-list":{"buildVersion":"1.2.3","testStatus":"PASSED","reportUrl":"https://docs.example.com/test",
            "residualIssues":[{"id":"issue-1","description":"保留一个遗留问题"}]}}}
            """, new TypeReference<>() {}));
        service.updateNode(DevelopmentItemType.TOPIC, topic, node, cmd);

        var loaded = service.detail(DevelopmentItemType.TOPIC, topic);
        var saved = loaded.getNodes().stream().filter(n -> node.equals(n.getId())).findFirst().orElseThrow();
        assertThat(saved.getFieldValues().get("__components").path("story-list").path("buildVersion").asText()).isEqualTo("1.2.3");
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getTasks()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT status FROM pms_development_item_workflow_node WHERE id=?", Integer.class, research)).isEqualTo(2);
        assertThat(mapper.readTree(jdbc.queryForObject("SELECT field_values_json FROM pms_development_item_workflow_node WHERE id=?", String.class, research)))
                .isEqualTo(mapper.readTree("{\"savedReport\":\"旧调研报告\"}"));
        assertThat(jdbc.queryForObject("SELECT progress FROM project_node_development_story WHERE id=?", Integer.class, story)).isEqualTo(37);
        assertThat(jdbc.queryForObject("SELECT topic_workflow_node_id FROM project_node_development_story WHERE id=?", Long.class, story)).isEqualTo(node);
        assertThat(jdbc.queryForObject("SELECT title FROM pms_development_item_task WHERE id=?", String.class, task)).isEqualTo("原有任务");
        assertThat(jdbc.queryForObject("SELECT template_version_id FROM pms_development_item_workflow WHERE id=?", Long.class, workflow)).isEqualTo(version);
    }

    private Long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
}
