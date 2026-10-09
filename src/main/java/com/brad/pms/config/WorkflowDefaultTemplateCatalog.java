package com.brad.pms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WorkflowDefaultTemplateCatalog {
    private final ObjectMapper objectMapper;

    public Optional<WorkflowDefaultTemplateFile> find(String processTypeCode) {
        ClassPathResource resource = new ClassPathResource("workflow-defaults/" + processTypeCode + ".json");
        if (!resource.exists()) return Optional.empty();
        try (var input = resource.getInputStream()) {
            return Optional.of(objectMapper.readValue(input, WorkflowDefaultTemplateFile.class));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("系统默认流程模板文件无法读取: " + processTypeCode, e);
        }
    }
}
