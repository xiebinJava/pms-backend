package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.WorkflowTemplateSaveCmd;
import com.brad.pms.dto.response.WorkflowTemplateDTO;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowTemplateMountBindingIntegrationTest {
    @Autowired WorkflowTemplateService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactionManager;

    @AfterEach
    void removeOnlyTestFixtures() {
        var ids = jdbc.queryForList("SELECT id FROM pms_workflow_template WHERE code='mount-parent-fixture' OR name IN ('mount-project-child','mount-story-child','mount-already-child','mount-conflict-child','mount-old-child','mount-concurrent-child')", Long.class);
        for (Long id : ids) {
            jdbc.update("DELETE FROM pms_workflow_template_version WHERE template_id=?", id);
            jdbc.update("DELETE FROM pms_workflow_template WHERE id=?", id);
        }
        jdbc.update("DELETE FROM pms_project_type WHERE code='mount-project-test'");
    }

    @Test
    void concurrentParentPublicationCannotRestoreAHostRemovedAfterTheChildTransactionSnapshot() throws Exception {
        long parent = parent("mount-project-test", true, projectDefinition("原控制节点", ""));
        WorkflowTemplateDefinition updated = mapper.readValue(
                projectDefinition("新控制节点", "").replace("mount-host-fixture", "new-host"),
                WorkflowTemplateDefinition.class);
        WorkflowTemplateSaveCmd childCommand = command("topic-management", "mount-concurrent-child", "sourceProjectNodeKey", null);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.execute(status -> {
            // Establish the repeatable-read snapshot before another transaction publishes a newer parent.
            jdbc.queryForObject("SELECT COUNT(*) FROM pms_workflow_template_version WHERE template_id=?", Integer.class, parent);
            CompletableFuture.runAsync(() -> {
                WorkflowTemplateSaveCmd cmd = new WorkflowTemplateSaveCmd();
                cmd.setName("mount-parent");
                cmd.setDefinition(updated);
                service.saveDraft(parent, cmd);
                service.publish(parent);
            }).orTimeout(10, TimeUnit.SECONDS).join();
            return service.saveDraft(null, childCommand);
        })).isInstanceOf(BusinessException.class).hasMessageContaining("父流程已发布节点发生变化");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_workflow_template_version WHERE template_id=? AND status='DRAFT'", Integer.class, parent)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_workflow_template WHERE name='mount-concurrent-child'", Integer.class)).isZero();
        assertThat(mapper.readTree(jdbc.queryForObject(
                "SELECT definition_json FROM pms_workflow_template_version WHERE template_id=? AND status='PUBLISHED' ORDER BY version_no DESC LIMIT 1", String.class, parent))
                .at("/nodes/0/key").asText()).isEqualTo("new-host");
    }

    @Test
    void savingTopicBindingCreatesOnlyAParentDraftAndKeepsRenamedNodeAndPublishedSnapshot() throws Exception {
        long parent = parent("mount-project-test", true, projectDefinition("项目控制", ""));
        String published = definition(parent, "PUBLISHED");
        WorkflowTemplateDTO child = save(null, "topic-management", "mount-project-child", "sourceProjectNodeKey", null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_workflow_template_version WHERE template_id=? AND status='DRAFT'", Integer.class, parent)).isEqualTo(1);
        JsonNode draft = mapper.readTree(definition(parent, "DRAFT"));
        assertThat(draft.at("/nodes/0/name").asText()).isEqualTo("项目控制");
        assertThat(draft.at("/nodes/0/contentOrder/1").asText()).isEqualTo("component:development-control");
        assertThat(draft.at("/nodes/0/fields/0/label").asText()).isEqualTo("保留字段");
        assertThat(definition(parent, "PUBLISHED")).isEqualTo(published);
        Integer revision = revision(parent);
        service.publish(child.getId());
        assertThat(revision(parent)).isEqualTo(revision);
        assertThat(definition(parent, "PUBLISHED")).isEqualTo(published);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_workflow_template_version WHERE template_id=?", Integer.class, parent)).isEqualTo(2);
    }

    @Test
    void savingStoryBindingMergesIntoTheCurrentTopicDraftAndDoesNotDuplicateTheWorkbench() throws Exception {
        long parent = parent("topic-management", false, topicDefinition("原节点", ""));
        String published = definition(parent, "PUBLISHED");
        String existingDraft = topicDefinition("开发管理", "component:topic-design-review");
        addDraft(parent, existingDraft);
        WorkflowTemplateDTO child = save(null, "story-management", "mount-story-child", "sourceTopicNodeKey", null);
        JsonNode draft = mapper.readTree(definition(parent, "DRAFT"));
        assertThat(draft.at("/sourceProjectNodeKey").asText()).isEqualTo("develop");
        assertThat(draft.at("/nodes/0/name").asText()).isEqualTo("开发管理");
        assertThat(draft.at("/nodes/0/contentOrder/1").asText()).isEqualTo("component:topic-design-review");
        assertThat(draft.at("/nodes/0/contentOrder/2").asText()).isEqualTo("component:story-list");
        assertThat(draft.at("/nodes/0/componentConfigs/topic-design-review/custom").asText()).isEqualTo("保留配置");
        Integer revision = revision(parent);
        save(child.getId(), "story-management", "mount-story-child", "sourceTopicNodeKey", child.getDraftRevision());
        assertThat(revision(parent)).isEqualTo(revision);
        assertThat(definition(parent, "PUBLISHED")).isEqualTo(published);
        assertThat(definition(parent, "DRAFT")).doesNotContain("story-split");
    }

    @Test
    void alreadyBoundLatestPublishedParentNeedsNoNewDraft() throws Exception {
        long parent = parent("mount-project-test", true, projectDefinition("项目控制", "component:development-control"));
        save(null, "topic-management", "mount-already-child", "sourceProjectNodeKey", null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_workflow_template_version WHERE template_id=?", Integer.class, parent)).isEqualTo(1);
    }

    @Test
    void parentDraftThatRemovedTheHostNodeCausesAtomicConflictInsteadOfOverwritingIt() throws Exception {
        long parent = parent("mount-project-test", true, projectDefinition("项目控制", ""));
        String removed = projectDefinition("其他节点", "").replace("mount-host-fixture", "other-host-fixture");
        addDraft(parent, removed);
        assertThatThrownBy(() -> save(null, "topic-management", "mount-conflict-child", "sourceProjectNodeKey", null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("草稿");
        assertThat(definition(parent, "DRAFT")).isEqualTo(removed);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_workflow_template WHERE name='mount-conflict-child'", Integer.class)).isZero();
    }

    @Test
    void aHostOnlyInAnOlderPublishedVersionIsNotAutoBound() throws Exception {
        long parent = parent("mount-project-test", true, projectDefinition("旧节点", ""));
        String latest = projectDefinition("新节点", "").replace("mount-host-fixture", "new-host-fixture");
        jdbc.update("INSERT INTO pms_workflow_template_version(template_id,version_no,status,definition_json) VALUES (?,2,'PUBLISHED',?)", parent, latest);
        jdbc.update("UPDATE pms_workflow_template SET latest_version_no=2 WHERE id=?", parent);
        assertThatThrownBy(() -> save(null, "topic-management", "mount-old-child", "sourceProjectNodeKey", null)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pms_workflow_template_version WHERE template_id=? AND status='DRAFT'", Integer.class, parent)).isZero();
    }

    private long parent(String typeCode, boolean projectEnabled, String json) {
        if (projectEnabled) jdbc.update("INSERT INTO pms_project_type(code,name,project_creation_enabled) VALUES (?, ?, TRUE)", typeCode, typeCode);
        long type = typeId(typeCode);
        jdbc.update("INSERT INTO pms_workflow_template(code,name,project_type_id,latest_version_no) VALUES ('mount-parent-fixture','父模板',?,1)", type);
        long id = jdbc.queryForObject("SELECT id FROM pms_workflow_template WHERE code='mount-parent-fixture'", Long.class);
        jdbc.update("INSERT INTO pms_workflow_template_version(template_id,version_no,status,definition_json) VALUES (?,1,'PUBLISHED',?)", id, json);
        return id;
    }

    private void addDraft(long parent, String json) {
        jdbc.update("INSERT INTO pms_workflow_template_version(template_id,version_no,status,definition_json) VALUES (?,2,'DRAFT',?)", parent, json);
        jdbc.update("UPDATE pms_workflow_template SET latest_version_no=2 WHERE id=?", parent);
    }

    private WorkflowTemplateDTO save(Long id, String type, String name, String mountField, Integer revision) throws Exception {
        return service.saveDraft(id, command(type, name, mountField, revision));
    }

    private WorkflowTemplateSaveCmd command(String type, String name, String mountField, Integer revision) throws Exception {
        WorkflowTemplateSaveCmd cmd = new WorkflowTemplateSaveCmd();
        cmd.setProjectTypeId(typeId(type)); cmd.setName(name); cmd.setExpectedDraftRevision(revision);
        String json = "{\"schemaVersion\":2,\"" + mountField + "\":\"mount-host-fixture\",\"nodes\":[{\"key\":\"child\",\"name\":\"子节点\",\"description\":\"\",\"deliverable\":\"\",\"roles\":\"\",\"fields\":[],\"contentOrder\":[]}]}";
        cmd.setDefinition(mapper.readValue(json, WorkflowTemplateDefinition.class));
        return cmd;
    }

    private long typeId(String code) { return jdbc.queryForObject("SELECT id FROM pms_project_type WHERE code=? AND deleted=FALSE", Long.class, code); }
    private String definition(long id, String status) { return jdbc.queryForObject("SELECT definition_json FROM pms_workflow_template_version WHERE template_id=? AND status=? ORDER BY version_no DESC LIMIT 1", String.class, id, status); }
    private Integer revision(long id) { return jdbc.queryForObject("SELECT version FROM pms_workflow_template_version WHERE template_id=? AND status='DRAFT'", Integer.class, id); }
    private String topicDefinition(String name, String component) { return projectDefinition(name, component).replace("\"schemaVersion\":2", "\"schemaVersion\":2,\"sourceProjectNodeKey\":\"develop\""); }
    private String projectDefinition(String name, String component) {
        return """
            {"schemaVersion":2,"nodes":[{"key":"mount-host-fixture","name":"%s","description":"保留说明","deliverable":"","roles":"",
            "fields":[{"key":"retained","label":"保留字段","type":"TEXT","required":false,"visible":true,"options":[]}],
            "contentOrder":["legacy-custom-fields"%s],"componentConfigs":%s}]}
            """.formatted(name, component.isEmpty() ? "" : ",\"" + component + "\"",
                    component.equals("component:topic-design-review") ? "{\"topic-design-review\":{\"custom\":\"保留配置\"}}" : "{}");
    }
}
