package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.DevelopmentStorySaveCmd;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.workflow.DevelopmentItemType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StandaloneTopicStoryIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DevelopmentStoryManagementService stories;
    @Autowired DevelopmentItemWorkflowService workflows;
    @Autowired SqlSessionFactory sessions;

    @BeforeEach
    void restoreApplicationMappings() {
        // Mock-only tests may replace MyBatis-Plus's shared metadata cache.
        var assistant = new MapperBuilderAssistant(sessions.getConfiguration(), "standalone-story-integration");
        for (var entity : java.util.List.of(DevelopmentItemWorkflowDO.class, DevelopmentItemWorkflowNodeDO.class,
                ProjectNodeDevelopmentStoryDO.class, ProjectNodeDevelopmentTopicDO.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @Test
    void createsStoryUnderStandaloneTopicWithPinnedVersionAndCorrectMount() {
        Fixture f = fixture();
        DevelopmentStorySaveCmd cmd = command(f.version()); cmd.setTopicId(f.topic());
        Long id = stories.create(cmd);
        var result = workflows.detail(DevelopmentItemType.STORY, id);
        assertThat(result.getTopicId()).isEqualTo(f.topic());
        assertThat(result.getProjectId()).isNull();
        assertThat(result.getTopicWorkflowNodeId()).isEqualTo(f.node());
        assertThat(result.getTemplateVersionId()).isEqualTo(f.version());
        assertThat(result.getNodes()).hasSize(1);
    }

    @Test
    void createsStoryWithoutATopic() {
        Fixture f = fixture();
        Long id = stories.create(command(f.version()));
        assertThat(workflows.detail(DevelopmentItemType.STORY, id).getTopicId()).isNull();
    }

    @Test
    void rejectsDeletedParentTopic() {
        Fixture f = fixture();
        jdbc.update("UPDATE project_node_development_topic SET deleted=TRUE WHERE id=?", f.topic());
        DevelopmentStorySaveCmd cmd = command(f.version()); cmd.setTopicId(f.topic());
        assertThatThrownBy(() -> stories.create(cmd)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsMountNodeBelongingToAnotherTopic() {
        Fixture f = fixture();
        jdbc.update("INSERT INTO project_node_development_topic(title) VALUES ('别的专题')");
        Long other = lastId();
        Long id = rawStory(other, f.node(), null);
        assertThatThrownBy(() -> workflows.createWithTemplate(DevelopmentItemType.STORY, id, null, null, f.version()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsProjectScopedParentForStandaloneStory() {
        Fixture f = fixture();
        jdbc.update("UPDATE project_node_development_topic SET project_id=999,node_id=999 WHERE id=?", f.topic());
        Long id = rawStory(f.topic(), f.node(), null);
        assertThatThrownBy(() -> workflows.createWithTemplate(DevelopmentItemType.STORY, id, null, null, f.version()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsTopicStoryWithoutAMountNode() {
        Fixture f = fixture();
        Long id = rawStory(f.topic(), null, null);
        assertThatThrownBy(() -> workflows.createWithTemplate(DevelopmentItemType.STORY, id, null, null, f.version()))
                .isInstanceOf(BusinessException.class);
    }

    private Fixture fixture() {
        Long type = jdbc.queryForObject("SELECT id FROM pms_project_type WHERE code='story-management'", Long.class);
        jdbc.update("INSERT INTO pms_workflow_template(code,name,project_type_id,latest_version_no) VALUES ('standalone-story-fixture','独立专题故事测试',?,1)", type);
        Long template = lastId();
        jdbc.update("INSERT INTO pms_workflow_template_version(template_id,version_no,status,definition_json) VALUES (?,1,'PUBLISHED',?)", template,
                "{\"schemaVersion\":2,\"sourceTopicNodeKey\":\"mount\",\"nodes\":[{\"key\":\"write\",\"name\":\"写卡\",\"fields\":[],\"contentOrder\":[]}]}");
        Long version = lastId();
        jdbc.update("INSERT INTO project_node_development_topic(title) VALUES ('独立专题')");
        Long topic = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow(item_type,item_id,template_version_id,story_mount_node_key,story_mount_template_version_id) VALUES ('TOPIC',?,?,'mount',?)", topic, version, version);
        Long workflow = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,sort,status) VALUES (?,'mount','开发与测试',0,1)", workflow);
        return new Fixture(topic, lastId(), version);
    }

    private Long rawStory(Long topic, Long node, Long project) {
        jdbc.update("INSERT INTO project_node_development_story(title,topic_id,topic_workflow_node_id,project_id) VALUES ('错误范围故事',?,?,?)", topic, node, project);
        return lastId();
    }
    private DevelopmentStorySaveCmd command(Long version) {
        var cmd = new DevelopmentStorySaveCmd(); cmd.setTitle("独立专题下故事"); cmd.setTemplateVersionId(version); return cmd;
    }
    private Long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
    private record Fixture(Long topic, Long node, Long version) {}
}
