package com.brad.pms.controller;

import com.brad.pms.config.DataInitializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:mysql:8.4:///bootstrap?TC_DAEMON=true&allowPublicKeyRetrieval=true&useSSL=false",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test", "spring.datasource.password=test",
        "pms.notification.startup-check-enabled=false"})
@ActiveProfiles("mysql")
@AutoConfigureMockMvc
class BootstrapPasswordSessionTest {
    static final String INITIAL = UUID.randomUUID().toString();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("PMS_BOOTSTRAP_ADMIN_PASSWORD", () -> INITIAL);
        registry.add("PMS_JWT_SECRET", () -> INITIAL);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired DataInitializer initializer;

    @Test void restrictedUntilChangeThenEveryOldSessionIsRevoked() throws Exception {
        var credentials = Map.of("email", "admin@example.com", "password", INITIAL);
        var user = call("/auth/login", credentials, "", 200);
        assertThat(user.at("/user/mustChangePassword").asBoolean()).isTrue();
        String access = user.path("accessToken").asText();
        String refresh = user.path("refreshToken").asText();
        assertThat(call("/auth/login", credentials, "", 200).at("/user/mustChangePassword").asBoolean()).isTrue();
        mvc.perform(get("/auth/me").header("Authorization","Bearer "+access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
        call("/development/topics/page", Map.of(), access, 403);
        call("/integration/cli/v1/authorize/approve", Map.of(), access, 403);
        call("/integration/cli/v1/token", Map.of("grantType","refresh_token","refreshToken",refresh), "", 403);
        var rotated = call("/auth/refresh", Map.of("refreshToken",refresh), "", 200);
        assertThat(rotated.at("/user/mustChangePassword").asBoolean()).isTrue();
        call("/development/topics/page", Map.of(), rotated.path("accessToken").asText(), 403);
        String next = UUID.randomUUID().toString();
        var wrong = mvc.perform(post("/auth/password/change").header("Authorization","Bearer "+access)
                        .contentType(APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("currentPassword",next,"newPassword",next))))
                .andReturn();
        assertThat(json.readTree(wrong.getResponse().getContentAsByteArray()).path("code").asInt()).isNotEqualTo(200);
        call("/development/topics/page", Map.of(), access, 403);
        call("/auth/password/change", Map.of("currentPassword",INITIAL,"newPassword",next), access, 200);
        call("/development/topics/page", Map.of(), access, 401);
        call("/auth/refresh", Map.of("refreshToken",rotated.path("refreshToken").asText()), "", 401);
        call("/auth/login", credentials, "", 401);
        initializer.run();
        var renewed = call("/auth/login", Map.of("email","admin@example.com","password",next), "", 200);
        assertThat(renewed.at("/user/mustChangePassword").asBoolean()).isFalse();
        call("/development/topics/page", Map.of(), renewed.path("accessToken").asText(), 200);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sys_user",Long.class)).isOne();
    }

    private JsonNode call(String path, Map<String,String> body, String token, int expected) throws Exception {
        var request = post(path).contentType(APPLICATION_JSON).content(json.writeValueAsBytes(body));
        if (!token.isEmpty()) request.header("Authorization","Bearer "+token);
        var result = mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(expected)).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }
}
