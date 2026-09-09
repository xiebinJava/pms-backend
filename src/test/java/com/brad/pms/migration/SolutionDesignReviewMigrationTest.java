package com.brad.pms.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SolutionDesignReviewMigrationTest {

    @Test
    void definesTheSinglePackageThreeReviewsAndSingleDecisionModel() throws IOException {
        String migration = read("db/migration/V18__solution_design_review.sql");
        String schema = read("schema.sql");

        for (String table : new String[]{
                "project_node_solution_package",
                "project_node_solution_review",
                "project_node_solution_decision"}) {
            assertThat(migration).containsIgnoringCase("CREATE TABLE " + table);
            assertThat(schema).containsIgnoringCase("CREATE TABLE IF NOT EXISTS " + table);
        }
        assertThat(migration).containsIgnoringCase("UNIQUE KEY uk_node_solution_package_project_node (project_id, node_id)");
        assertThat(migration).containsIgnoringCase("UNIQUE KEY uk_node_solution_review_type (project_id, node_id, review_type)");
        assertThat(migration).containsIgnoringCase("UNIQUE KEY uk_node_solution_decision_project_node (project_id, node_id)");
        assertThat(migration).containsIgnoringCase("BUSINESS_PRODUCT");
        assertThat(migration).containsIgnoringCase("TEST_RELEASE");
        assertThat(migration).containsIgnoringCase("CONDITIONAL_PASS");
        assertThat(migration).containsIgnoringCase("version");
        assertThat(migration).containsIgnoringCase("NOT NULL DEFAULT 0");
        assertThat(migration).containsIgnoringCase("idx_node_solution_review_node (project_id, node_id)");
        assertThat(migration).doesNotContain("package_version  VARCHAR(20)  NOT NULL")
                .doesNotContain("product_solution VARCHAR(4000) NOT NULL")
                .doesNotContain("technical_solution VARCHAR(4000) NOT NULL");
    }

    @Test
    void addsAnOptionalReviewerToEachRequiredReview() throws IOException {
        String migration = read("db/migration/V22__solution_design_reviewers.sql");
        String schema = read("schema.sql");

        assertThat(migration).containsIgnoringCase("ADD COLUMN reviewer_id BIGINT NULL");
        assertThat(migration).containsIgnoringCase("idx_node_solution_review_reviewer");
        assertThat(schema).containsIgnoringCase("reviewer_id   BIGINT");
    }

    @Test
    void removesTheDeletedSolutionPackageFields() throws IOException {
        String migration = read("db/migration/V23__remove_solution_package_legacy_fields.sql");
        String schema = read("schema.sql");

        assertThat(migration).containsIgnoringCase("DROP COLUMN package_version")
                .containsIgnoringCase("DROP COLUMN summary")
                .containsIgnoringCase("DROP COLUMN scope_coverage")
                .containsIgnoringCase("DROP COLUMN rollout_premise")
                .containsIgnoringCase("DROP COLUMN reason");
        String packageTable = schema.substring(schema.indexOf("CREATE TABLE IF NOT EXISTS project_node_solution_package"));
        packageTable = packageTable.substring(0, packageTable.indexOf("CREATE TABLE IF NOT EXISTS project_node_solution_review"));
        String decisionTable = schema.substring(schema.indexOf("CREATE TABLE IF NOT EXISTS project_node_solution_decision"));
        decisionTable = decisionTable.substring(0, decisionTable.indexOf("\n);") + 3);
        assertThat(packageTable).doesNotContainIgnoringCase("package_version")
                .doesNotContainIgnoringCase("scope_coverage")
                .doesNotContainIgnoringCase("rollout_premise")
                .doesNotContainIgnoringCase("summary");
        assertThat(decisionTable).doesNotContainIgnoringCase("reason");
    }

    private String read(String resource) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            if (stream == null) return "";
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
