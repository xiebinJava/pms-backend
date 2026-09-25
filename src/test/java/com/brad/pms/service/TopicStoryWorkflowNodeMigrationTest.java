package com.brad.pms.service;

import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentStoryListDTO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TopicStoryWorkflowNodeMigrationTest {

    @Test
    void migrationDefinesVersionedMountAndTopicWorkflowNodeContext() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V55__topic_story_workflow_node_context.sql"));

        assertThat(sql).contains(
                "ADD COLUMN topic_workflow_node_id BIGINT NULL",
                "ADD COLUMN project_mount_node_key VARCHAR(80) NULL",
                "ADD COLUMN story_mount_template_version_id BIGINT NULL",
                "ADD COLUMN story_mount_node_key VARCHAR(80) NULL",
                "idx_development_story_topic_workflow_node",
                "pms_development_workflow_migration_issue");
        assertThat(sql).doesNotContain("= 'develop'");
    }

    @Test
    void domainAndResponseModelsExposeTheTwoDifferentWorkflowContexts() {
        assertThat(hasField(ProjectNodeDevelopmentStoryDO.class, "topicWorkflowNodeId")).isTrue();
        assertThat(hasField(DevelopmentItemWorkflowDO.class, "projectMountNodeKey")).isTrue();
        assertThat(hasField(DevelopmentItemWorkflowDO.class, "storyMountTemplateVersionId")).isTrue();
        assertThat(hasField(DevelopmentItemWorkflowDO.class, "storyMountNodeKey")).isTrue();
        assertThat(hasField(DevelopmentStoryListDTO.class, "topicWorkflowNodeId")).isTrue();
        assertThat(hasField(DevelopmentStoryListDTO.class, "topicWorkflowNodeName")).isTrue();
        assertThat(hasField(DevelopmentItemWorkflowDetailDTO.class, "topicWorkflowNodeId")).isTrue();
        assertThat(hasField(DevelopmentItemWorkflowDetailDTO.class, "topicWorkflowNodeName")).isTrue();
    }

    private boolean hasField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            return field != null;
        } catch (NoSuchFieldException ignored) {
            return false;
        }
    }
}
