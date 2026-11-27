package com.brad.pms.service;

import com.brad.pms.dto.request.DevelopmentItemPageQry;
import com.brad.pms.dto.response.DevelopmentStoryListDTO;
import com.brad.pms.dto.response.DevelopmentTopicListDTO;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:tc:mysql:8.4:///paging?TC_DAEMON=true&allowPublicKeyRetrieval=true&useSSL=false")
@Transactional
class DevelopmentItemServiceTest {
    @Autowired DevelopmentItemService items;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;

    @BeforeAll
    static void prepareLegacyCollations(@Autowired JdbcTemplate db) {
        // Keep the different utf8mb4 collations found in an upgraded database.
        db.execute("ALTER TABLE project MODIFY name VARCHAR(100) COLLATE utf8mb4_0900_ai_ci NOT NULL");
        db.execute("ALTER TABLE project_node_development_topic MODIFY title VARCHAR(200) COLLATE utf8mb4_unicode_ci NOT NULL");
        db.execute("ALTER TABLE project_node_development_story MODIFY title VARCHAR(300) COLLATE utf8mb4_unicode_ci NOT NULL");
    }

    @BeforeEach void fixtures() {
        UserContext.set(new LoginUser(1L, "admin", "admin", 1));
        db.update("INSERT INTO project(id,code,name,owner_id) VALUES (810001,'PG-fixture','分页工程',1)");
        db.update("INSERT INTO project_node(id,project_id,node_key,name,sort) VALUES (810002,810001,'PG','分页节点',4)");
        db.update("INSERT INTO project_node_iteration_plan(id,project_id,node_id,name) VALUES (810003,810001,810002,'第一个迭代')");
        db.update("""
                INSERT INTO project_node_development_topic(id,project_id,node_id,title,owner_id,deleted) VALUES
                (810004,810001,810002,'PG%_中文',1,false),(810005,810001,810002,'PG%_中文',1,false),
                (810006,810001,810002,'PG已删除',1,true),(810007,NULL,NULL,'PG未绑定',NULL,false)
                """);
        db.update("""
                INSERT INTO project_node_development_story(id,project_id,node_id,topic_id,iteration_plan_id,
                    title,owner_id,status,progress,blocker,due_date) VALUES
                (810011,810001,810002,810004,810003,'PG故事',1,'DONE',0,NULL,'2026-09-30'),
                (810012,810001,810002,810004,NULL,'PG故事',1,'BLOCKED',30,'等待联调',NULL),
                (810013,810001,810002,810006,NULL,'PG回收站故事',1,'IN_PROGRESS',40,NULL,NULL),
                (810014,NULL,NULL,810007,NULL,'PG专题故事',NULL,'NOT_STARTED',0,NULL,NULL),
                (810015,NULL,NULL,NULL,NULL,'PG独立故事',NULL,'NOT_STARTED',0,NULL,NULL)
                """);
    }
    @AfterEach void clear() { UserContext.clear(); }

    @Test void matchesLegacyPageSnapshotsForEveryReader() throws Exception {
        long member = db.queryForObject("SELECT id FROM sys_user WHERE username_normalized='alex.zhang'", Long.class);
        db.update("INSERT INTO project_member(project_id,user_id,role) VALUES (810001,?,2)", member);
        db.update("INSERT INTO pms_requirement(id,title,owner_id,execution_target_type,execution_target_id) "
                + "VALUES (810101,'分页来源',1,'TOPIC',810004)");
        db.update("INSERT INTO pms_development_item_workflow(id,item_type,item_id,project_id,source_node_id,template_version_id) "
                + "SELECT 810102,'TOPIC',810004,810001,810002,MIN(id) FROM pms_workflow_template_version");
        db.update("INSERT INTO pms_development_item_workflow_node(workflow_id,node_key,name,status) "
                + "VALUES (810102,'done','已完成',2)");
        db.update("INSERT INTO project_node_development_topic(id,project_id,title) VALUES "
                + "(810021,810001,'PGA'),(810022,810001,'PGa'),(810023,810001,'PG中'),(810024,810001,'PG😀')");
        for (long reader : new long[]{1L,member,888888L}) {
            UserContext.set(new LoginUser(reader, "reader", "reader", reader == 1 ? 1 : 0));
            var digest = MessageDigest.getInstance("SHA-256");
            for (boolean topics : new boolean[]{true,false}) {
                for (boolean deleted : new boolean[]{false,true}) {
                    if (!topics && deleted) continue;
                    for (int size : new int[]{1,100}) {
                        var q = new DevelopmentItemPageQry();
                        q.setKeyword("PG");
                        q.setPageSize(size);
                        q.setDeleted(deleted);
                        for (int number = 1; ; number++) {
                            q.setCurrPage(number);
                            var page = topics ? items.pageTopics(q) : items.pageStories(q);
                            digest.update(json.writeValueAsBytes(page));
                            if (page.getList().isEmpty()) break;
                        }
                    }
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            // Full response snapshots from the pre-pagination implementation at 47e47c2.
            String expected = reader == 1 ? "5a5c930986ade8ef4df9681ac4c730026ec3b874699ac00802a7aac9665bebde"
                    : reader == member ? "2ad79847f6300bb73abf13197107a1f4973d3422e2dd8cf8fa13544e231a1d1a"
                    : "cc663783fe0d0df0eee9e7d6fb1ed5ef5b0feeadddd0020af22c2d52ac940f25";
            assertThat(actual).as("legacy page snapshot for reader %d", reader).isEqualTo(expected);
        }
    }

    @Test void filtersBeforePagingAndPreservesTopicSummaries() {
        var q = new DevelopmentItemPageQry();
        q.setProjectId(810001L);
        q.setKeyword(" %_ ");
        q.setPageSize(1);
        var page = items.pageTopics(q);
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getTotalPage()).isEqualTo(2);
        var topic = page.getList().get(0);
        assertThat(topic.getId()).isEqualTo(810004);
        assertThat(topic.getNodeName()).isEqualTo("分页节点");
        assertThat(topic.getProjectName()).isEqualTo("分页工程");
        assertThat(topic.getOwnerName()).isNotBlank();
        assertThat(topic.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(topic.getDevelopmentProgress()).isEqualTo(65);
        assertThat(topic.getStoryCount()).isEqualTo(2);
        assertThat(topic.getCompletedStoryCount()).isOne();
        assertThat(topic.getBlockedStoryCount()).isOne();
        assertThat(topic.getBlocker()).isEqualTo("等待联调");
        q.setCurrPage(2);
        assertThat(items.pageTopics(q).getList()).extracting(DevelopmentTopicListDTO::getId).containsExactly(810005L);
        q.setCurrPage(Integer.MAX_VALUE);
        assertThat(items.pageTopics(q).getList()).isEmpty();
        q.setCurrPage(1);
        q.setStatus("not_started");
        assertThat(items.pageTopics(q).getList()).extracting(DevelopmentTopicListDTO::getId).containsExactly(810005L);
        q.setStatus("NÖT_STARTED");
        assertThat(items.pageTopics(q).getTotal()).isZero();
        q.setStatus("NOT_STARTED");
        q.setKeyword(topic.getOwnerName());
        assertThat(items.pageTopics(q).getTotal()).isOne();
    }

    @Test void legacyWhitespaceUsesNicknameForDisplayAndSearch() {
        db.update("UPDATE sys_user SET name_zh=CHAR(9), nickname=CONCAT(CHAR(9),'旧昵称',CHAR(10)) WHERE id=1");
        var q = new DevelopmentItemPageQry();
        q.setProjectId(810001L);
        q.setKeyword("旧昵称");
        var result = items.pageTopics(q);
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getList().get(0).getOwnerName()).isEqualTo("旧昵称（admin）");
    }

    @Test void filtersStoriesOnParentTitleAndLoadsIteration() {
        var q = new DevelopmentItemPageQry();
        q.setKeyword("%_中文");
        q.setStatus("done");
        q.setOwnerId(1L);
        q.setNodeId(810002L);
        var page = items.pageStories(q);
        assertThat(page.getTotal()).isOne();
        var story = page.getList().get(0);
        assertThat(story.getTopicTitle()).isEqualTo("PG%_中文");
        assertThat(story.getIterationPlanName()).isEqualTo("第一个迭代");
        assertThat(story.getDevelopmentProgress()).isEqualTo(100);
        assertThat(story.getDueDate()).isEqualTo(LocalDate.of(2026,9,30));
        assertThat(story.getOwnerName()).isNotBlank();
    }

    @Test void searchesPagesWhenProjectAndTitlesUseDifferentCollations() {
        db.update("UPDATE project SET name='ΟΣ MIXED' WHERE id=810001");
        var q = new DevelopmentItemPageQry();
        q.setProjectId(810001L);
        q.setKeyword("ος");
        q.setPageSize(1);

        var topics = items.pageTopics(q);
        assertThat(topics.getTotal()).isEqualTo(2);
        assertThat(topics.getList()).extracting(DevelopmentTopicListDTO::getId).containsExactly(810004L);
        var stories = items.pageStories(q);
        assertThat(stories.getTotal()).isEqualTo(2);
        assertThat(stories.getList()).extracting(DevelopmentStoryListDTO::getId).containsExactly(810011L);

        q.setCurrPage(2);
        assertThat(items.pageTopics(q).getList()).extracting(DevelopmentTopicListDTO::getId).containsExactly(810005L);
        assertThat(items.pageStories(q).getList()).extracting(DevelopmentStoryListDTO::getId).containsExactly(810012L);
    }

    @Test void keywordUsesJavaCaseMappingBeforeCountAndPaging() {
        db.update("""
                INSERT INTO project_node_development_topic(id,project_id,title) VALUES
                (810031,810001,'ΟΣ'),(810032,810001,'ΟΣΑ'),(810033,810001,'İSTANBUL')
                """);
        db.update("""
                INSERT INTO project_node_development_story(id,project_id,topic_id,title) VALUES
                (810041,810001,810031,'a'),(810042,810001,810031,'b'),(810043,810001,810032,'c')
                """);
        db.update("UPDATE project_node SET name='K unicode-node' WHERE id=810002");

        var q = new DevelopmentItemPageQry();
        q.setKeyword("ος");
        q.setPageSize(1);
        assertThat(items.pageTopics(q).getList()).extracting(DevelopmentTopicListDTO::getId).containsExactly(810031L);
        assertThat(items.pageStories(q).getTotal()).isEqualTo(2);
        assertThat(items.pageStories(q).getList()).extracting(DevelopmentStoryListDTO::getId).containsExactly(810041L);
        q.setCurrPage(2);
        assertThat(items.pageStories(q).getList()).extracting(DevelopmentStoryListDTO::getId).containsExactly(810042L);

        q.setCurrPage(1);
        q.setKeyword("οσ");
        assertThat(items.pageTopics(q).getList()).extracting(DevelopmentTopicListDTO::getId).containsExactly(810032L);
        q.setKeyword("stanbul");
        assertThat(items.pageTopics(q).getTotal()).isOne();
        q.setKeyword("istanbul");
        assertThat(items.pageTopics(q).getTotal()).isZero();
        q.setKeyword("i̇stanbul");
        assertThat(items.pageTopics(q).getList()).extracting(DevelopmentTopicListDTO::getId).containsExactly(810033L);
        q.setKeyword("k unicode-node");
        assertThat(items.pageTopics(q).getTotal()).isEqualTo(2);
    }

    @Test void recycleBinUsesManagePermissionIncludingClosedProjects() {
        db.update("UPDATE project SET status=4,deleted=true WHERE id=810001");
        var q = new DevelopmentItemPageQry();
        q.setProjectId(810001L);
        assertThat(items.pageTopics(q).getTotal()).isZero();
        q.setDeleted(true);
        var topic = items.pageTopics(q).getList().get(0);
        assertThat(topic.getId()).isEqualTo(810006L);
        assertThat(topic.getStoryCount()).isOne();
        assertThat(topic.getProjectName()).isEqualTo("分页工程");
        UserContext.set(new LoginUser(888888L, "reader", "reader", 0));
        assertThat(items.pageTopics(q).getTotal()).isZero();
    }

    @Test void isolatedReaderSeesIndependentItemsAndNoHiddenChildren() {
        // Legacy inconsistent bindings still belong to their stored project.
        db.update("INSERT INTO project_node_development_story(project_id,title) VALUES (810001,'PG遗留绑定')");
        UserContext.set(new LoginUser(888888L, "reader", "reader", 0));
        var q = new DevelopmentItemPageQry();
        q.setKeyword("PG");
        assertThat(items.pageTopics(q).getList()).extracting(DevelopmentTopicListDTO::getId).containsExactly(810007L);
        assertThat(items.pageStories(q).getList()).extracting(DevelopmentStoryListDTO::getId)
                .containsExactly(810015L,810014L);
        q.setProjectId(810001L);
        assertThat(items.pageTopics(q).getTotal()).isZero();
        assertThat(items.pageStories(q).getTotal()).isZero();
    }
}
