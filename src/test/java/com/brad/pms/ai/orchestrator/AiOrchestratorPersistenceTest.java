package com.brad.pms.ai.orchestrator;

import com.baomidou.mybatisplus.annotation.TableName;
import com.brad.pms.ai.orchestrator.persistence.AiConversationDO;
import com.brad.pms.ai.orchestrator.persistence.AiConversationMapper;
import com.brad.pms.ai.orchestrator.persistence.AiOperationEventDO;
import com.brad.pms.ai.orchestrator.persistence.AiOperationEventMapper;
import com.brad.pms.ai.orchestrator.persistence.AiPlanDO;
import com.brad.pms.ai.orchestrator.persistence.AiPlanMapper;
import com.brad.pms.ai.orchestrator.persistence.AiConversationTurnDO;
import com.brad.pms.ai.orchestrator.persistence.AiConversationTurnMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AiOrchestratorPersistenceTest {

    @Test
    void mapsTheFourOrchestratorTables() {
        assertThat(AiConversationDO.class.getAnnotation(TableName.class).value())
                .isEqualTo("pms_ai_conversation");
        assertThat(AiConversationTurnDO.class.getAnnotation(TableName.class).value())
                .isEqualTo("pms_ai_conversation_turn");
        assertThat(AiPlanDO.class.getAnnotation(TableName.class).value())
                .isEqualTo("pms_ai_plan");
        assertThat(AiOperationEventDO.class.getAnnotation(TableName.class).value())
                .isEqualTo("pms_ai_operation_event");
    }

    @Test
    void exposesUserScopedAndLockedPlanReads() throws NoSuchMethodException {
        assertThat(AiConversationMapper.class.getMethod("selectByIdAndUserId", Long.class, Long.class))
                .isNotNull();
        assertThat(AiPlanMapper.class.getMethod("selectByIdAndConversationUser", Long.class, Long.class))
                .isNotNull();
        Select lockedSelect = AiPlanMapper.class.getMethod("selectForUpdate", Long.class, Long.class)
                .getAnnotation(Select.class);
        assertThat(lockedSelect).isNotNull();
        assertThat(String.join(" ", lockedSelect.value()))
                .contains("user_id = #{userId}")
                .contains("FOR UPDATE");
        Update stateUpdate = AiPlanMapper.class.getMethod("updateState", Long.class, Long.class,
                String.class, String.class, String.class, java.time.LocalDateTime.class)
                .getAnnotation(Update.class);
        assertThat(stateUpdate).isNotNull();
        assertThat(String.join(" ", stateUpdate.value())).contains("user_id = #{userId}");
    }

    @Test
    void exposesOrderedEventAppendAndReadMethods() throws NoSuchMethodException {
        assertThat(AiOperationEventMapper.class.getMethod("appendNext", Long.class, Long.class,
                String.class, String.class, String.class)).isNotNull();
        Select eventRead = AiOperationEventMapper.class.getMethod("selectAfterSequence", Long.class, Long.class,
                Long.class, int.class).getAnnotation(Select.class);
        assertThat(eventRead).isNotNull();
        assertThat(String.join(" ", eventRead.value()))
                .contains("user_id = #{userId}")
                .contains("e.conversation_id = #{conversationId}")
                .doesNotContain("event e e JOIN");
        Select sequenceRead = AiOperationEventMapper.class.getMethod("selectNextSequenceForUpdate", Long.class)
                .getAnnotation(Select.class);
        assertThat(sequenceRead).isNotNull();
        assertThat(String.join(" ", sequenceRead.value()))
                .contains("event_sequence")
                .contains("FOR UPDATE");
        Update sequenceUpdate = AiOperationEventMapper.class.getMethod("incrementSequence", Long.class)
                .getAnnotation(Update.class);
        assertThat(sequenceUpdate).isNotNull();
        assertThat(String.join(" ", sequenceUpdate.value())).contains("event_sequence = event_sequence + 1");
        assertThat(AiConversationTurnMapper.class.getMethod("selectByIdAndConversationId", Long.class, Long.class))
                .isNotNull();
    }

    @Test
    void migrationContainsUserIsolationAndEventSequenceConstraints() throws IOException {
        String migration;
        try (InputStream stream = getClass().getResourceAsStream("/db/migration/V70__ai_orchestrator.sql")) {
            assertThat(stream).as("V70 migration").isNotNull();
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(migration)
                .contains("CREATE TABLE pms_ai_conversation")
                .contains("CREATE TABLE pms_ai_conversation_turn")
                .contains("CREATE TABLE pms_ai_plan")
                .contains("CREATE TABLE pms_ai_operation_event")
                .contains("UNIQUE KEY uk_pms_ai_operation_event_plan_sequence")
                .contains("KEY idx_pms_ai_conversation_user")
                .contains("KEY idx_pms_ai_plan_conversation");

        String sequenceMigration;
        try (InputStream stream = getClass().getResourceAsStream(
                "/db/migration/V71__ai_orchestrator_conversation_event_sequence.sql")) {
            assertThat(stream).as("V71 migration").isNotNull();
            sequenceMigration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sequenceMigration)
                .contains("ADD COLUMN event_sequence BIGINT NOT NULL DEFAULT 0")
                .contains("CREATE TABLE IF NOT EXISTS pms_ai_event_sequence_rebuild")
                .contains("DELETE FROM pms_ai_event_sequence_rebuild")
                .contains("UPDATE pms_ai_operation_event\nSET sequence_no = -id")
                .contains("DROP INDEX uk_pms_ai_operation_event_plan_sequence")
                .contains("ROW_NUMBER() OVER (PARTITION BY conversation_id")
                .contains("DROP TABLE pms_ai_event_sequence_rebuild")
                .contains("ADD UNIQUE KEY uk_pms_ai_operation_event_conversation_sequence")
                .doesNotContain("CREATE TEMPORARY TABLE");
    }

    @Test
    void entityClassesCanBeLoadedWithoutADataSource() {
        assertThatCode(() -> {
            AiConversationDO.class.getDeclaredConstructor().newInstance();
            AiConversationTurnDO.class.getDeclaredConstructor().newInstance();
            AiPlanDO.class.getDeclaredConstructor().newInstance();
            AiOperationEventDO.class.getDeclaredConstructor().newInstance();
        }).doesNotThrowAnyException();
    }
}
