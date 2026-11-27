package com.brad.pms.controller;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import com.atlassian.oai.validator.model.Request;
import com.brad.pms.security.UserContext;
import com.brad.pms.dto.request.LoginRequest;
import com.brad.pms.mapper.UserMapper;
import com.brad.pms.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:tc:mysql:8.4:///contract?TC_DAEMON=true&allowPublicKeyRetrieval=true&useSSL=false")
@AutoConfigureMockMvc
@Transactional
class DevelopmentPageContractTest {
    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
            .createForSpecificationUrl(DevelopmentPageContractTest.class.getResource("/openapi/pms-api.yaml").toString())
            .build();
    private static final ResultMatcher CONTRACT = openApi().isValid(VALIDATOR);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired AuthService auth;
    @Autowired UserMapper users;
    @Autowired ObjectMapper json;

    @AfterEach
    void clearRequestContext() {
        UserContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"topics", "stories", "requirements"})
    void validatesPopulatedFilteredDefaultAndEmptyPages(String resource) throws Exception {
        db.update("INSERT INTO project_node_development_topic (title) VALUES ('contract-fixture')");
        db.update("INSERT INTO project_node_development_story (title) VALUES ('contract-fixture')");
        db.update("INSERT INTO pms_requirement (title, execution_target_type, execution_target_id) VALUES ('contract-fixture', 'TOPIC', 900000001)");
        String token = login("admin");
        String path = "/api/development/" + resource + "/page";
        JsonNode page = success(post(path).contentType(APPLICATION_JSON)
                .content("{\"keyword\":\"contract-fixture\"}"), token);
        assertThat(page.path("total").asInt()).isEqualTo(1);
        assertThat(page.at("/list/0/title").asText()).isEqualTo("contract-fixture");
        assertThat(page.path("currPage").asInt()).isOne();
        assertThat(page.path("pageSize").asInt()).isEqualTo(10);
        assertThat(success(post(path), token).path("total").asInt()).isPositive();
        assertThat(success(post(path).contentType(APPLICATION_JSON)
                .content("{\"keyword\":\"contract-no-match\"}"), token).path("list")).isEmpty();
        if (resource.equals("requirements")) {
            assertThat(page.at("/list/0/executionTarget/status").asText()).isEqualTo("UNAVAILABLE");
            assertThat(success(get(path).queryParam("keyword", "contract-fixture"), token)).isEqualTo(page);
        }
    }

    @Test
    void deniedAccountReceivesDocumentedErrors() throws Exception {
        String name = "Alex.Zhang";
        String token = login(name);
        db.update("UPDATE sys_user_role SET status='ENDED' WHERE user_id=?",
                users.findByUsernameNormalized("alex.zhang").getId());
        for (var request : List.of(post("/api/development/topics/page"), post("/api/development/stories/page"),
                post("/api/development/requirements/page"), get("/api/development/requirements/page"))) {
            mvc.perform(request.contextPath("/api").header("Authorization", token))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403)).andExpect(CONTRACT);
            var anonymous = mvc.perform(request.with(req -> { req.removeHeader("Authorization"); return req; }))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.requestId").isString()).andReturn();
            var report = VALIDATOR.validateResponse(anonymous.getRequest().getRequestURI(),
                    Request.Method.valueOf(anonymous.getRequest().getMethod()), MockMvcResponse.of(anonymous.getResponse()));
            assertThat(report.getMessages()).isEmpty();
        }
    }

    private JsonNode success(MockHttpServletRequestBuilder request, String token) throws Exception {
        var result = mvc.perform(request.contextPath("/api").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(CONTRACT).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private String login(String username) {
        var request = new LoginRequest();
        request.setUsername(username);
        request.setPassword("admin123");
        return "Bearer " + auth.login(request, "127.0.0.1", "contract-test").getAccessToken();
    }
}
