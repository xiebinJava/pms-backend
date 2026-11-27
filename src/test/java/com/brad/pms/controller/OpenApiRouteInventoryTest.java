package com.brad.pms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OpenApiRouteInventoryTest {

    @Autowired @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void publicRoutesAndOpenApiHaveNoNewDifferences() throws Exception {
        Set<String> actual = new TreeSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            // Framework error pages and actuator endpoints are outside the PMS API.
            if (!handler.getBeanType().getPackageName().startsWith("com.brad.pms.")) return;
            assertThat(mapping.getMethodsCondition().getMethods()).as("Explicit HTTP methods for %s", mapping.getPatternValues()).isNotEmpty();
            for (var method : mapping.getMethodsCondition().getMethods()) {
                for (String path : mapping.getPatternValues()) {
                    actual.add(method.name() + " " + path.replaceAll("\\{(\\w+):[^}]+}", "{$1}"));
                }
            }
        });

        Set<String> documented = new TreeSet<>();
        try (var input = getClass().getResourceAsStream("/openapi/pms-api.yaml")) {
            JsonNode spec = new ObjectMapper(new YAMLFactory()).readTree(input);
            assertThat(spec.at("/servers/0/url").asText()).isEqualTo("/api");
            spec.path("paths").fields().forEachRemaining(entry ->
                    entry.getValue().fieldNames().forEachRemaining(verb -> {
                        if (Set.of("get", "post", "put", "delete", "patch", "head", "options", "trace").contains(verb)) {
                            documented.add(verb.toUpperCase(Locale.ROOT) + " " + entry.getKey());
                        }
                    }));
        }
        Set<String> gaps = new TreeSet<>();
        actual.stream().filter(route -> !documented.contains(route)).forEach(route -> gaps.add("undocumented " + route));
        documented.stream().filter(route -> !actual.contains(route)).forEach(route -> gaps.add("unregistered " + route));
        try (var input = getClass().getResourceAsStream("/openapi-route-debt.txt")) {
            Set<String> baseline = new TreeSet<>(new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .lines().filter(line -> !line.isBlank() && !line.startsWith("#")).toList());
            assertThat(gaps).as("Differences between Spring MVC routes and OpenAPI").isEqualTo(baseline);
        }
    }
}
