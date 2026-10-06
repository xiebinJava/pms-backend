package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.workflow.DevelopmentItemType;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemTaskDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
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
class StoryWorkflowStatusIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DevelopmentItemWorkflowService service;
    @Autowired SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void restoreApplicationMappingsAfterMapperUnitTests() {
        var assistant = new MapperBuilderAssistant(sqlSessionFactory.getConfiguration(), "workflow-status-integration");
        for (var entity : java.util.List.of(DevelopmentItemWorkflowNodeDO.class, DevelopmentItemWorkflowDO.class,
                DevelopmentItemTaskDO.class, ProjectNodeDevelopmentStoryDO.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @Test
    void completingNodesSynchronizesStoredBusinessStatusAndProgress() {
        Fixture f = fixture();
        var first = service.completeNode(DevelopmentItemType.STORY, f.story(), f.first());
        assertThat(first.getDevelopmentStatus()).isEqualTo("IN_PROGRESS");
        assertThat(first.getDevelopmentProgress()).isEqualTo(50);
        assertStored(f.story(), "IN_PROGRESS", 50);
        var done = service.completeNode(DevelopmentItemType.STORY, f.story(), f.last());
        assertThat(done.getDevelopmentStatus()).isEqualTo("DONE");
        assertThat(done.getDevelopmentProgress()).isEqualTo(100);
        assertStored(f.story(), "DONE", 100);
    }

    @Test
    void rollingBackTerminalNodeRestoresInProgressAndPreservesHistory() {
        Fixture f = completedFixture();
        service.rollbackNode(DevelopmentItemType.STORY, f.story(), f.last(), "验收需要补充");
        assertStored(f.story(), "IN_PROGRESS", 50);
        assertThat(jdbc.queryForObject("SELECT field_values_json FROM pms_development_item_workflow_node WHERE id=?", String.class, f.last()))
                .contains("历史数据");
    }

    @Test
    void rollingBackFirstNodeResetsProgressWithoutMarkingStoryNotStarted() {
        Fixture f = completedFixture();
        service.rollbackNode(DevelopmentItemType.STORY, f.story(), f.first(), "范围调整");
        assertStored(f.story(), "IN_PROGRESS", 0);
        assertThat(service.detail(DevelopmentItemType.STORY, f.story()).getNodes())
                .extracting(n -> n.getStatus()).containsExactly(1, 0);
    }

    @Test
    void blockedCompletionDoesNotChangeBusinessStatusOrNode() {
        Fixture f = fixture();
        jdbc.update("INSERT INTO pms_development_item_task(workflow_id,node_id,title,status) VALUES (?,?,'未完成任务',0)", f.workflow(), f.first());
        assertThatThrownBy(() -> service.completeNode(DevelopmentItemType.STORY, f.story(), f.first()))
                .isInstanceOf(BusinessException.class);
        assertStored(f.story(), "NOT_STARTED", 0);
        assertThat(jdbc.queryForObject("SELECT status FROM pms_development_item_workflow_node WHERE id=?", Integer.class, f.first())).isEqualTo(1);
    }

    private Fixture completedFixture() {
        Fixture f = fixture();
        jdbc.update("UPDATE pms_development_item_workflow_node SET status=2 WHERE workflow_id=?", f.workflow());
        jdbc.update("UPDATE project_node_development_story SET status='DONE',progress=100 WHERE id=?", f.story());
        return f;
    }

    private Fixture fixture() {
        Long type = jdbc.queryForObject("SELECT id FROM pms_project_type WHERE code='story-management'", Long.class);
        jdbc.update("INSERT INTO pms_workflow_template(code,name,project_type_id,latest_version_no) VALUES ('status-e2e-fixture','状态回归模板',?,1)", type);
        Long template = lastId();
        jdbc.update("INSERT INTO pms_workflow_template_version(template_id,version_no,status,definition_json) VALUES (?,1,'PUBLISHED',?)", template,
                "{\"schemaVersion\":2,\"nodes\":[{\"key\":\"first\",\"name\":\"第一节点\",\"fields\":[],\"contentOrder\":[]},{\"key\":\"last\",\"name\":\"最后节点\",\"fields\":[],\"contentOrder\":[]}]}");
        Long version = lastId();
        jdbc.update("INSERT INTO project_node_development_story(title,status,progress) VALUES ('状态回归故事','NOT_STARTED',0)");
        Long story = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow(item_type,item_id,template_version_id) VALUES ('STORY',?,?)", story, version);
        Long workflow = lastId();
        Long owner = jdbc.queryForObject("SELECT id FROM sys_user WHERE deleted=FALSE LIMIT 1", Long.class);
        jdbc.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,sort,status,owner_id,start_date,end_date) VALUES (?,'first','第一节点',0,1,?,'2026-10-06','2026-10-30')", workflow, owner);
        Long first = lastId();
        jdbc.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,sort,status,owner_id,start_date,end_date,field_values_json) VALUES (?,'last','最后节点',1,0,?,'2026-10-06','2026-10-30','{\"history\":\"历史数据\"}')", workflow, owner);
        return new Fixture(story, workflow, first, lastId());
    }

    private void assertStored(Long id, String status, int progress) {
        assertThat(jdbc.queryForMap("SELECT status,progress FROM project_node_development_story WHERE id=?", id))
                .containsEntry("status", status).containsEntry("progress", progress);
    }
    private Long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
    private record Fixture(Long story, Long workflow, Long first, Long last) {}
}
