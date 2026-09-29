package com.brad.pms.config;

import com.brad.pms.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

/** Writes a selected local workflow default into the checked-out source tree. */
@Component
@RequiredArgsConstructor
public class WorkflowSystemDefaultWriter {
    private static final Set<String> LOCAL_ENVIRONMENTS = Set.of("local", "development", "dev");

    private final ObjectMapper objectMapper;
    private final WorkflowDefaultProperties properties;
    private final Environment environment;

    public Path write(WorkflowDefaultTemplateFile template) {
        ensureLocalWriteEnabled();
        if (template == null || blank(template.processTypeCode()) || template.definition() == null) {
            throw BusinessException.error("系统默认流程模板内容无效");
        }

        Path root = Path.of(properties.getSourceDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            Path target = root.resolve(template.processTypeCode() + ".json").normalize();
            if (!root.equals(target.getParent())) {
                throw BusinessException.error("系统默认流程模板路径无效");
            }
            Path temporary = Files.createTempFile(root, ".workflow-default-", ".tmp");
            try {
                byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(template);
                Files.write(temporary, json);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return target;
        } catch (IOException e) {
            throw BusinessException.error("系统默认流程模板写入失败，请检查源码目录权限");
        }
    }

    private void ensureLocalWriteEnabled() {
        String deploymentEnvironment = environment.getProperty("pms.deployment.environment", "production")
                .trim().toLowerCase(Locale.ROOT);
        if (!LOCAL_ENVIRONMENTS.contains(deploymentEnvironment)) {
            throw BusinessException.forbidden("固化系统默认模板仅允许在本地开发环境执行");
        }
        if (!properties.isWriteEnabled()) {
            throw BusinessException.forbidden("本地系统默认模板写入未开启");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
