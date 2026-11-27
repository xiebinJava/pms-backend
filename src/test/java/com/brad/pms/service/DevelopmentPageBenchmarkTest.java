package com.brad.pms.service;

import com.brad.pms.dto.request.DevelopmentItemPageQry;
import com.brad.pms.mapper.DevelopmentPageMapper;
import com.brad.pms.security.LoginUser;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.springframework.jdbc.core.ConnectionCallback;
import com.brad.pms.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@EnabledIfSystemProperty(named = "pms.pageBenchmark", matches = "true")
class DevelopmentPageBenchmarkTest {
    @Autowired DevelopmentItemService items;
    @Autowired ProjectService projectService;
    @Autowired JdbcTemplate db;
    @Autowired SqlSession sql;
    @Autowired ObjectMapper json;

    @AfterEach void clear() { UserContext.clear(); }

    @Test void measureIdenticalPagesAtTwoSizes() throws Exception {
        UserContext.set(new LoginUser(1L, "admin", "admin", 1));
        db.execute("SET SESSION cte_max_recursion_depth=10001");
        for (int batch = 0; batch < 2; batch++) {
            int base = 500000 + batch * 10000;
            for (int j = 0; j < 20; j++) {
                long id = base + j;
                db.update("INSERT INTO project(id,code,name,owner_id) VALUES (?,?,?,1)", id, "B"+id, "工程"+id);
                db.update("INSERT INTO project_node(id,project_id,node_key,name) VALUES (?,?,?,?)",
                        id, id, "node", "节点"+id);
            }
            db.update("""
                    INSERT INTO project_node_development_topic(id,project_id,node_id,title)
                    WITH RECURSIVE seq(n) AS (SELECT 0 UNION ALL SELECT n+1 FROM seq WHERE n<999)
                    SELECT ?+n, ?+MOD(n,20), ?+MOD(n,20), CONCAT('bench_',MOD(n,17)) FROM seq
                    """, base, base, base);
            db.update("""
                    INSERT INTO project_node_development_story(id,topic_id,project_id,node_id,title,status,progress)
                    WITH RECURSIVE seq(n) AS (SELECT 0 UNION ALL SELECT n+1 FROM seq WHERE n<9999)
                    SELECT ?+n, ?+FLOOR(n/10), ?+MOD(FLOOR(n/10),20), ?+MOD(FLOOR(n/10),20), CONCAT('bench_',MOD(n,23)),
                    IF(MOD(n,2)=0,'DONE','BLOCKED'),30 FROM seq
                    """, base, base, base, base);
            for (boolean topics : new boolean[]{true,false}) {
                DevelopmentItemPageQry query = new DevelopmentItemPageQry();
                query.setKeyword("bench_");
                query.setCurrPage(3);
                long[] samples = new long[20];
                for (int i = -2; i < samples.length; i++) {
                    sql.clearCache();
                    long start = System.nanoTime();
                    var page = topics ? items.pageTopics(query) : items.pageStories(query);
                    if (i >= 0) samples[i] = System.nanoTime() - start;
                    assertThat(page.getTotal()).isEqualTo((batch+1) * (topics ? 1000 : 10000));
                    if (i == 0) {
                        var hash = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(page));
                        System.out.printf("page-hash %d %s %s%n", batch, topics, HexFormat.of().formatHex(hash));
                    }
                }
                var scope = new DevelopmentPageMapper.Scope(query, projectService.listReadableIds());
                for (String statement : (topics ? List.of("countTopics","topicIds") : List.of("countStories","storyIds"))) {
                    explain(statement, scope);
                }
                Arrays.sort(samples);
                System.out.printf("page-p95 %d %s %.1f ms%n", batch, topics, samples[18] / 1e6);
            }
        }
    }

    private void explain(String name, DevelopmentPageMapper.Scope scope) {
        var statement = sql.getConfiguration().getMappedStatement(DevelopmentPageMapper.class.getName() + "." + name);
        var bound = statement.getBoundSql(scope);
        db.execute((ConnectionCallback<Void>) connection -> {
            try (var query = connection.prepareStatement("EXPLAIN ANALYZE " + bound.getSql())) {
                new DefaultParameterHandler(statement, scope, bound).setParameters(query);
                try (var result = query.executeQuery()) {
                    while (result.next()) System.out.printf("page-plan %s %s%n", name, result.getString(1));
                }
            }
            return null;
        });
    }
}
