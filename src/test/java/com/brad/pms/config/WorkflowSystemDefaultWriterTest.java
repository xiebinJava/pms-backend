package com.brad.pms.config;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.workflow.BuiltInWorkflowTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowSystemDefaultWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void restoresPreviousFileWhenDatabaseTransactionRollsBack() throws Exception {
        Path target = tempDir.resolve("general.json");
        Files.writeString(target, "original");
        WorkflowDefaultProperties properties = new WorkflowDefaultProperties();
        properties.setWriteEnabled(true);
        properties.setSourceDir(tempDir.toString());
        WorkflowSystemDefaultWriter writer = new WorkflowSystemDefaultWriter(new ObjectMapper(), properties,
                new MockEnvironment().withProperty("pms.deployment.environment", "local"));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            writer.write(new WorkflowDefaultTemplateFile("general", "current-process", "默认", "", 2,
                    BuiltInWorkflowTemplate.compatibilityDefinition()));
            assertThat(Files.readString(target)).contains("versionNo");
            for (var synchronization : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            assertThat(Files.readString(target)).isEqualTo("original");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void writesDefaultTemplateAsReadableSourceJsonInLocalEnabledMode() throws Exception {
        WorkflowDefaultProperties properties = new WorkflowDefaultProperties();
        properties.setWriteEnabled(true);
        properties.setSourceDir(tempDir.toString());
        WorkflowSystemDefaultWriter writer = new WorkflowSystemDefaultWriter(
                new ObjectMapper(), properties, new MockEnvironment()
                        .withProperty("pms.deployment.environment", "local"));

        Path written = writer.write(new WorkflowDefaultTemplateFile(
                "general", "current-process", "当前项目流程", "项目默认流程", 2,
                BuiltInWorkflowTemplate.compatibilityDefinition()));

        assertThat(written).isEqualTo(tempDir.resolve("general.json").toAbsolutePath());
        assertThat(Files.readString(written))
                .contains("\"processTypeCode\" : \"general\"")
                .contains("\"templateCode\" : \"current-process\"")
                .contains("\"versionNo\" : 1")
                .contains("\"name\" : \"项目管理流程\"")
                .contains("\"nodes\"");
    }

    @Test
    void refusesToWriteOutsideLocalDevelopment() throws Exception {
        WorkflowDefaultProperties properties = new WorkflowDefaultProperties();
        properties.setWriteEnabled(true);
        properties.setSourceDir(tempDir.toString());
        WorkflowSystemDefaultWriter writer = new WorkflowSystemDefaultWriter(
                new ObjectMapper(), properties, new MockEnvironment()
                        .withProperty("pms.deployment.environment", "production"));

        assertThatThrownBy(() -> writer.write(new WorkflowDefaultTemplateFile(
                "general", "current-process", "当前项目流程", "项目默认流程", 2,
                BuiltInWorkflowTemplate.compatibilityDefinition())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅允许在本地开发环境");
        assertThat(Files.list(tempDir).toList()).isEmpty();
    }

    @Test
    void refusesToWriteWhenFeatureFlagIsDisabled() throws Exception {
        WorkflowDefaultProperties properties = new WorkflowDefaultProperties();
        properties.setWriteEnabled(false);
        properties.setSourceDir(tempDir.toString());
        WorkflowSystemDefaultWriter writer = new WorkflowSystemDefaultWriter(
                new ObjectMapper(), properties, new MockEnvironment()
                        .withProperty("pms.deployment.environment", "local"));

        assertThatThrownBy(() -> writer.write(new WorkflowDefaultTemplateFile(
                "general", "current-process", "当前项目流程", "项目默认流程", 2,
                BuiltInWorkflowTemplate.compatibilityDefinition())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未开启");
        assertThat(Files.list(tempDir).toList()).isEmpty();
    }
}
