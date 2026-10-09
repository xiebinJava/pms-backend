package com.brad.pms.config;

import com.brad.pms.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/** Writes a selected local workflow default into the checked-out source tree. */
@Component
@RequiredArgsConstructor
public class WorkflowSystemDefaultWriter {
    private static final Set<String> LOCAL_ENVIRONMENTS = Set.of("local", "development", "dev");

    private final ObjectMapper objectMapper;
    private final WorkflowDefaultProperties properties;
    private final Environment environment;
    private final ReentrantLock writeLock = new ReentrantLock();

    public boolean isAvailable() {
        try {
            ensureLocalWriteEnabled();
            Path root = Path.of(properties.getSourceDir()).toAbsolutePath().normalize();
            return Files.isDirectory(root) && Files.isWritable(root);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public Path write(WorkflowDefaultTemplateFile template) {
        ensureLocalWriteEnabled();
        if (template == null || blank(template.processTypeCode()) || template.definition() == null) {
            throw BusinessException.error("系统默认流程模板内容无效");
        }

        Path root = Path.of(properties.getSourceDir()).toAbsolutePath().normalize();
        writeLock.lock();
        boolean deferredUnlock = false;
        try {
            Files.createDirectories(root);
            Path target = root.resolve(template.processTypeCode() + ".json").normalize();
            if (!root.equals(target.getParent())) {
                throw BusinessException.error("系统默认流程模板路径无效");
            }
            byte[] previous = Files.exists(target) ? Files.readAllBytes(target) : null;
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        try {
                            if (status == STATUS_ROLLED_BACK) {
                                if (previous == null) Files.deleteIfExists(target);
                                else replaceAtomically(root, target, previous);
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("恢复系统默认流程模板失败: " + target, e);
                        } finally {
                            writeLock.unlock();
                        }
                    }
                });
                deferredUnlock = true;
            }
            Path temporary = Files.createTempFile(root, ".workflow-default-", ".tmp");
            try {
                // A source default is the baseline of a new installation, not this database's history.
                String name = switch (template.processTypeCode()) {
                    case "general" -> "项目管理流程";
                    case "requirement-management" -> "需求管理流程";
                    case "topic-management" -> "专题管理流程";
                    case "story-management" -> "故事管理流程";
                    default -> template.name();
                };
                var baseline = new WorkflowDefaultTemplateFile(template.processTypeCode(), template.templateCode(),
                        name, template.description(), 1, template.definition());
                byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(baseline);
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
        } finally {
            if (!deferredUnlock) writeLock.unlock();
        }
    }

    private void replaceAtomically(Path root, Path target, byte[] content) throws IOException {
        Path temporary = Files.createTempFile(root, ".workflow-restore-", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
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
