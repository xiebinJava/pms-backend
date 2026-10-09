package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.entity.*;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.security.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;

/** Real MySQL fixtures roll back; no writes to the user's database. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IterationSystemBindingIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired IterationPlanCommandService commands;
    @Autowired RequirementSystemReferenceService requirements;
    @Autowired IterationPlanOptionsService options;
    @Autowired RequirementMapper requirementMapper;
    @Autowired RequirementManagementService requirementManagement;
    @Autowired IterationPlanSystemService binding;
    @Autowired ProjectNodeDevelopmentStoryMapper storyMapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SqlSessionFactory sessions;

    @BeforeEach void applicationMappings() {
        UserContext.clear();
        var assistant = new MapperBuilderAssistant(sessions.getConfiguration(), "iteration-system-integration");
        for (var entity : java.util.List.of(ProjectNodeIterationPlanDO.class, RequirementDO.class,
                ProjectNodeDevelopmentStoryDO.class, ProjectNodeDevelopmentTopicDO.class,
                SystemDO.class, SystemVersionDO.class, ProjectTaskDO.class)) TableInfoHelper.initTableInfo(assistant, entity);
    }

    @Test void createsAManualSystemIterationAndPersistsExplicitVersionClearing() {
        Long system = system("one");
        Long version = version(system, "PLANNED");
        NodeIterationPlanCmd cmd = command(system);
        cmd.setSystemVersionId(version);
        Long plan = commands.create(null, null, cmd);
        assertThat(value("system_id", plan)).isEqualTo(system);
        assertThat(value("system_version_id", plan)).isEqualTo(version);
        NodeIterationPlanCmd update = command(system);
        update.setSystemVersionId(null);
        commands.update(plan, update);
        assertThat(value("system_version_id", plan)).isNull();
        assertThat(value("system_id", plan)).isEqualTo(system);
    }

    @Test void inheritsFromADirectTopicRequirementAndFillsALegacyIteration() {
        Long system = system("one");
        jdbc.update("INSERT INTO project_node_development_topic(title) VALUES ('独立专题')");
        Long topic = lastId();
        jdbc.update("INSERT INTO project_node_development_story(title,topic_id) VALUES ('独立专题故事',?)", topic);
        Long story = lastId();
        jdbc.update("INSERT INTO pms_requirement(title,system_id,execution_target_type,execution_target_id) VALUES ('直接专题需求',?,'TOPIC',?)", system, topic);
        assertThat(requirements.resolveForStory(story)).isEqualTo(system);
        jdbc.update("INSERT INTO project_node_iteration_plan(name,status) VALUES ('历史独立迭代','PLANNED')");
        Long plan = lastId();
        commands.addStory(plan, story);
        assertThat(value("system_id", plan)).isEqualTo(system);
        assertThat(value("project_id", plan)).isNull();
        assertThat(jdbc.queryForObject("SELECT iteration_plan_id FROM project_node_development_story WHERE id=?", Long.class, story)).isEqualTo(plan);
        assertThat(options.forStory(story)).extracting(p -> p.getId()).contains(plan);
        commands.removeStory(plan, story);
        assertThat(jdbc.queryForObject("SELECT iteration_plan_id FROM project_node_development_story WHERE id=?", Long.class, story)).isNull();
        assertThat(value("system_id", plan)).isEqualTo(system);
    }

    @Test void rejectsCrossSystemVersionAndStoryAssociations() {
        Long first = system("one");
        Long second = system("two");
        NodeIterationPlanCmd cmd = command(first);
        cmd.setSystemVersionId(version(second, "PLANNED"));
        assertThatThrownBy(() -> commands.create(null, null, cmd)).isInstanceOf(BusinessException.class);
        Long plan = commands.create(null, null, command(first));
        Long story = storyWithRequirement(second);
        assertThatThrownBy(() -> commands.addStory(plan, story)).isInstanceOf(BusinessException.class);
        assertThat(value("system_id", plan)).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT iteration_plan_id FROM project_node_development_story WHERE id=?", Long.class, story)).isNull();
        assertThat(options.forStory(story)).extracting(p -> p.getId()).doesNotContain(plan);
    }

    @Test void changingARequirementCannotInvalidateAnExistingIterationBinding() {
        Long first = system("one");
        Long second = system("two");
        Long plan = commands.create(null, null, command(first));
        Long story = storyWithRequirement(first);
        commands.addStory(plan, story);
        Long requirementId = jdbc.queryForObject("SELECT id FROM pms_requirement WHERE execution_target_type='STORY' AND execution_target_id=?", Long.class, story);
        RequirementDO requirement = requirementMapper.selectById(requirementId);
        requirement.setSystemId(second);
        assertThatThrownBy(() -> requirements.validateRequirementSystem(requirement))
                .isInstanceOf(BusinessException.class).hasMessageContaining("迭代");
        assertThat(value("system_id", plan)).isEqualTo(first);
    }

    @Test void unchangedReleasedVersionSurvivesEditingButCannotBeNewlySelected() {
        Long system = system("one");
        Long version = version(system, "PLANNED");
        NodeIterationPlanCmd create = command(system);
        create.setSystemVersionId(version);
        Long plan = commands.create(null, null, create);
        jdbc.update("UPDATE pms_system_version SET status='RELEASED' WHERE id=?", version);
        commands.update(plan, command(system));
        assertThat(value("system_version_id", plan)).isEqualTo(version);
        assertThatThrownBy(() -> commands.create(null, null, create)).isInstanceOf(BusinessException.class);
    }

    @Test void fillingABlankIterationChecksItsAlreadyLinkedStories() {
        Long first = system("one"), second = system("two");
        jdbc.update("INSERT INTO project_node_iteration_plan(name,status) VALUES ('旧迭代','PLANNED')");
        Long plan = lastId();
        Long existing = storyWithRequirement(first);
        jdbc.update("UPDATE project_node_development_story SET iteration_plan_id=? WHERE id=?", plan, existing);
        assertThatThrownBy(() -> commands.addStory(plan, storyWithRequirement(second)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("系统");
        assertThat(value("system_id", plan)).isNull();
    }

    @Test void restoringARequirementCannotCreateACrossSystemLink() {
        Long first = system("one"), second = system("two");
        Long story = storyWithRequirement(first);
        Long id = jdbc.queryForObject("SELECT id FROM pms_requirement WHERE execution_target_id=? AND execution_target_type='STORY'", Long.class, story);
        jdbc.update("UPDATE pms_requirement SET deleted=TRUE WHERE id=?", id);
        commands.addStory(commands.create(null, null, command(second)), story);
        assertThatThrownBy(() -> requirementManagement.restore(id))
                .isInstanceOf(BusinessException.class).hasMessageContaining("系统");
        assertThat(jdbc.queryForObject("SELECT deleted FROM pms_requirement WHERE id=?", Boolean.class, id)).isTrue();
    }

    @Test void clearingARequirementSystemPersistsNull() {
        Long story = storyWithRequirement(system("one"));
        Long id = jdbc.queryForObject("SELECT id FROM pms_requirement WHERE execution_target_id=? AND execution_target_type='STORY'", Long.class, story);
        RequirementDO requirement = requirementMapper.selectByIdForUpdate(id);
        requirement.setSystemId(null);
        requirementMapper.updateById(requirement);
        assertThat(jdbc.queryForObject("SELECT system_id FROM pms_requirement WHERE id=?", Long.class, id)).isNull();
    }

    @Test void movingASystemSourceIntoAProjectChecksAllDestinationIterations() {
        Long first = system("one"), second = system("two");
        jdbc.update("INSERT INTO project(code,name,owner_id) VALUES ('iteration-destination','目标项目',1)");
        Long project = lastId();
        jdbc.update("INSERT INTO project_node_development_story(title,project_id) VALUES ('已有故事',?)", project);
        Long existing = lastId();
        Long plan = commands.create(null, null, command(second));
        jdbc.update("UPDATE project_node_development_story SET iteration_plan_id=? WHERE id=?", plan, existing);
        Long moved = storyWithRequirement(first);
        ProjectNodeDevelopmentStoryDO scope = new ProjectNodeDevelopmentStoryDO();
        scope.setId(moved); scope.setProjectId(project);
        assertThatThrownBy(() -> binding.validateStoryScope(scope))
                .isInstanceOf(BusinessException.class).hasMessageContaining("系统");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSourceChangesCannotRacePastStoryAssociation() throws Exception {
        Long first = system("race-one"), second = system("race-two");
        Long story = storyWithRequirement(first);
        Long id = jdbc.queryForObject("SELECT id FROM pms_requirement WHERE execution_target_id=? AND execution_target_type='STORY'", Long.class, story);
        Long plan = commands.create(null, null, command(first));
        var executor = Executors.newFixedThreadPool(2);
        var linked = new CountDownLatch(1);
        var commitLink = new CountDownLatch(1);
        var editStarted = new CountDownLatch(1);
        var tx = new TransactionTemplate(transactions);
        try {
            var link = executor.submit(() -> tx.executeWithoutResult(status -> {
                var row = storyMapper.selectByIdForUpdate(story);
                binding.bindStory(plan, row);
                linked.countDown();
                try { if (!commitLink.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("association timed out"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                storyMapper.updateIterationPlan(story, plan);
            }));
            assertThat(linked.await(5, TimeUnit.SECONDS)).isTrue();
            var change = executor.submit(() -> {
                editStarted.countDown();
                tx.executeWithoutResult(status -> {
                    var row = requirementMapper.selectByIdForUpdate(id);
                    row.setSystemId(second);
                    requirements.validateRequirementSystem(row);
                    requirementMapper.updateById(row);
                });
            });
            assertThat(editStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> change.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            commitLink.countDown();
            link.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> change.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessException.class);
            assertThat(jdbc.queryForObject("SELECT system_id FROM pms_requirement WHERE id=?", Long.class, id)).isEqualTo(first);
            assertThat(jdbc.queryForObject("SELECT iteration_plan_id FROM project_node_development_story WHERE id=?", Long.class, story)).isEqualTo(plan);
        } finally {
            commitLink.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
            jdbc.update("DELETE FROM pms_requirement WHERE id=?", id);
            jdbc.update("DELETE FROM project_node_development_story WHERE id=?", story);
            jdbc.update("DELETE FROM project_node_iteration_plan WHERE id=?", plan);
            jdbc.update("DELETE FROM pms_system WHERE id IN (?,?)", first, second);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void iterationSystemChangesUseCurrentStoryScopesInsteadOfAnEarlierSnapshot() {
        Long first = system("snapshot-one"), second = system("snapshot-two");
        Long plan = commands.create(null, null, command(first));
        jdbc.update("INSERT INTO project_node_development_story(title,iteration_plan_id) VALUES ('快照故事',?)", plan);
        Long story = lastId();
        jdbc.update("INSERT INTO project_node_development_topic(title) VALUES ('系统专题')");
        Long topic = lastId();
        jdbc.update("INSERT INTO pms_requirement(title,system_id,execution_target_type,execution_target_id) VALUES ('专题系统需求',?,'TOPIC',?)", first, topic);
        Long requirement = lastId();
        var earlier = new TransactionTemplate(transactions);
        var newer = new TransactionTemplate(transactions);
        newer.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            earlier.executeWithoutResult(status -> {
                status.setRollbackOnly();
                assertThat(jdbc.queryForObject("SELECT topic_id FROM project_node_development_story WHERE id=?", Long.class, story)).isNull();
                newer.executeWithoutResult(newStatus -> {
                    var row = storyMapper.selectByIdForUpdate(story);
                    row.setTopicId(topic);
                    binding.validateStoryScope(row);
                    storyMapper.updateScope(story, topic, null, null, null);
                });
                assertThatThrownBy(() -> commands.update(plan, command(second)))
                        .isInstanceOf(BusinessException.class).hasMessageContaining("系统");
            });
            assertThat(value("system_id", plan)).isEqualTo(first);
        } finally {
            jdbc.update("DELETE FROM pms_requirement WHERE id=?", requirement);
            jdbc.update("DELETE FROM project_node_development_story WHERE id=?", story);
            jdbc.update("DELETE FROM project_node_development_topic WHERE id=?", topic);
            jdbc.update("DELETE FROM project_node_iteration_plan WHERE id=?", plan);
            jdbc.update("DELETE FROM pms_system WHERE id IN (?,?)", first, second);
        }
    }

    private NodeIterationPlanCmd command(Long system) {
        NodeIterationPlanCmd cmd = new NodeIterationPlanCmd();
        cmd.setName("系统迭代测试"); cmd.setSystemId(system); cmd.setStatus("PLANNED"); return cmd;
    }
    private Long system(String code) {
        jdbc.update("INSERT INTO pms_system(name) VALUES (?)", "系统" + code); return lastId();
    }
    private Long version(Long system, String status) {
        jdbc.update("INSERT INTO pms_system_version(system_id,version_no,version_name,status) VALUES (?,'1.0','测试版',?)", system, status); return lastId();
    }
    private Long storyWithRequirement(Long system) {
        jdbc.update("INSERT INTO project_node_development_story(title) VALUES ('独立故事')");
        Long story = lastId();
        jdbc.update("INSERT INTO pms_requirement(title,system_id,execution_target_type,execution_target_id) VALUES ('直接故事需求',?,'STORY',?)", system, story);
        return story;
    }
    private Long value(String column, Long plan) {
        return jdbc.queryForObject("SELECT " + column + " FROM project_node_iteration_plan WHERE id=?", Long.class, plan);
    }
    private Long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
}
