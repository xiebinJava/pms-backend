package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DevelopmentControlMigrationTest {

    @Test
    void createsTheDevelopmentBaselineTopicAndStoryTables() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V27__development_control_workbench.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("CREATE TABLE project_node_development_baseline");
        assertThat(sql).containsIgnoringCase("CREATE TABLE project_node_development_topic");
        assertThat(sql).containsIgnoringCase("CREATE TABLE project_node_development_story");
        assertThat(sql).containsIgnoringCase("current_iteration");
        assertThat(sql).containsIgnoringCase("story_points");
        assertThat(sql).containsIgnoringCase("status");
        assertThat(sql).containsIgnoringCase("progress");
    }

    @Test
    void addsStoryOwnerColumnInANewMigration() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V28__development_control_story_owner.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("ALTER TABLE project_node_development_story");
        assertThat(sql).containsIgnoringCase("owner_id");
    }

    @Test
    void addsOptionalStoryLinkToTasksInANewMigration() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V29__development_story_task_link.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("ALTER TABLE project_task");
        assertThat(sql).containsIgnoringCase("development_story_id");
        assertThat(sql).containsIgnoringCase("idx_task_development_story");
    }

    @Test
    void addsOptionalDueDateToDevelopmentStoriesInANewMigration() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V34__add_development_story_due_date.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("ALTER TABLE project_node_development_story");
        assertThat(sql).containsIgnoringCase("due_date DATE");
    }

    @Test
    void removesTheTaskStoryAssociationInANewMigration() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V35__remove_development_story_task_link.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("ALTER TABLE project_task");
        assertThat(sql).containsIgnoringCase("DROP INDEX idx_task_development_story");
        assertThat(sql).containsIgnoringCase("DROP COLUMN development_story_id");
    }

    @Test
    void addsStoryStartDateInANewMigration() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V36__add_development_story_start_date.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).containsIgnoringCase("ALTER TABLE project_node_development_story");
        assertThat(sql).containsIgnoringCase("start_date DATE");
    }
}
