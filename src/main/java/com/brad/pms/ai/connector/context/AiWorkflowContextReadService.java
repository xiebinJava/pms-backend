package com.brad.pms.ai.connector.context;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.workflow.DevelopmentItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pure-read transactional facade for the AI workflow context: main-object module READ plus
 * reference projection happen inside one REPEATABLE READ transaction; the focused budget
 * gates the final concentrated response.
 */
@Service
@RequiredArgsConstructor
public class AiWorkflowContextReadService {

    private final AiProjectContextReader projectReader;
    private final AiDevelopmentContextReader developmentReader;
    private final AiContextResponseBudget budget;
    private final AiContextReadPolicy contextReadPolicy;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AiWorkflowContextDTO context(String resourceType, Long resourceId, ContextReadOptions options) {
        ContextReadOptions safeOptions = options == null ? ContextReadOptions.legacy() : options;
        if (resourceId == null) throw BusinessException.notFound("资源不存在");
        String type = normalize(resourceType);
        // 主对象模块 READ：requirement→REQUIREMENT_READ，其余→PROJECT_READ；查询范围不等于模块读取权限。
        contextReadPolicy.requireRead(type);
        AiWorkflowContextDTO context = switch (type) {
            case "project" -> projectReader.read(resourceId, safeOptions);
            case "requirement", "topic", "story" -> developmentReader.read(
                    DevelopmentItemType.valueOf(normalizedItemType(type)), resourceId, safeOptions);
            default -> throw BusinessException.error("不支持的流程上下文资源类型: " + type);
        };
        budget.requireWithinFocusedLimit(context);
        return context;
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT)
                .replace('-', '_');
        if (normalized.isBlank()) throw BusinessException.notFound("资源不存在");
        return normalized;
    }

    private String normalizedItemType(String type) {
        return type.equals("requirement") ? "REQUIREMENT"
                : type.equals("topic") ? "TOPIC" : "STORY";
    }

}
