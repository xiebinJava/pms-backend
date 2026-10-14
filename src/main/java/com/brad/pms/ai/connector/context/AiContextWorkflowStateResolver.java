package com.brad.pms.ai.connector.context;

import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure workflow status resolution per the design status table: entity facts never overwrite
 * inconsistent node facts, warnings declare problems instead of repairing them.
 */
@org.springframework.stereotype.Component
public final class AiContextWorkflowStateResolver {

    private static final String WARNING_INCONSISTENT = "INCONSISTENT_WORKFLOW_STATE";
    private static final String WARNING_NO_ACTIVE_NODE = "NO_ACTIVE_NODE";

    public record State(String status, String terminalStatus, List<AiWorkflowContextDTO.Warning> warnings) {
        public State {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public State resolve(String type, String entityStatus, String terminalStatus, boolean configured,
                         List<NodeRef> nodes) {
        Set<AiWorkflowContextDTO.Warning> warnings = new LinkedHashSet<>();
        String safeType = type == null ? "" : type;
        boolean projectLike = "project".equals(safeType);
        boolean requirementLike = "requirement".equals(safeType);

        // 不配置：状态固定 NOT_CONFIGURED；仅在存在实体终态事实时保留终态并声明不一致。
        if (!configured) {
            String entityTerminal = notConfiguredTerminal(projectLike, requirementLike, entityStatus);
            if (entityTerminal != null) {
                warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                        "对象处于已定义终态但流程未配置"));
                return new State("NOT_CONFIGURED", entityTerminal, new ArrayList<>(warnings));
            }
            return new State("NOT_CONFIGURED", null, new ArrayList<>(warnings));
        }

        // 实际运行的研发流程实例总是有终态候选：REJECTED 或空；未知非空终态保留事实并判定不一致。
        String resolvedTerminal = normalizeInstanceTerminal(terminalStatus);
        boolean terminalUnknown = terminalStatus != null && !terminalStatus.isBlank()
                && !"REJECTED".equals(terminalStatus);
        if (requirementLike && "REJECTED".equals(entityStatus)) resolvedTerminal = "REJECTED";
        if (terminalUnknown) {
            warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                    "流程终态状态非法或未知"));
            return new State("INCONSISTENT", terminalStatus, new ArrayList<>(warnings));
        }
        if (nodes == null || nodes.isEmpty()) {
            warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                    "流程已配置但没有实际节点"));
            warnings.add(noActive());
            return new State("INCONSISTENT", resolvedTerminal, new ArrayList<>(warnings));
        }
        if (nodesMissingUnknown(nodes)) {
            warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                    "流程节点状态非法或未知"));
            return new State("INCONSISTENT", resolvedTerminal, new ArrayList<>(warnings));
        }

        long activeCount = nodes.stream().filter(node -> node != null && Objects.equals(node.status(), 1)).count();
        long unknownCount = nodes.stream().filter(node -> node != null
                && (node.status() == null || node.status() < 0 || node.status() > 3)).count();
        if (unknownCount > 0) {
            warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                    "流程节点状态非法或未知"));
            return new State("INCONSISTENT", resolvedTerminal, new ArrayList<>(warnings));
        }

        if (projectLike) {
            LifeCycle lifeCycle = LifeCycle.of(entityStatus);
            if (lifeCycle == LifeCycle.UNKNOWN) {
                warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                        "项目生命周期状态未知"));
                return new State("INCONSISTENT", resolvedTerminal, new ArrayList<>(warnings));
            }
            if (lifeCycle == LifeCycle.TERMINATED) {
                if (activeCount > 0 || resolvedTerminal != null) {
                    warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                            "项目已终止但流程状态冲突"));
                    return new State("INCONSISTENT", "TERMINATED", new ArrayList<>(warnings));
                }
                return new State("TERMINATED", "TERMINATED", new ArrayList<>(warnings));
            }
            if (lifeCycle == LifeCycle.COMPLETED) {
                boolean allCompleted = nodes.stream().allMatch(node -> node != null && Objects.equals(node.status(), 2));
                if (!allCompleted) {
                    warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                            "项目已完成但流程节点未全部完成"));
                    return new State("INCONSISTENT", "COMPLETED", new ArrayList<>(warnings));
                }
            }
        }

        if (resolvedTerminal != null) {
            if (activeCount > 0) {
                warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                        "流程已到达终态但仍有进行中的节点"));
                return new State("INCONSISTENT", resolvedTerminal, new ArrayList<>(warnings));
            }
            return new State(resolvedTerminal, resolvedTerminal, new ArrayList<>(warnings));
        }

        long completedCount = nodes.stream().filter(node -> node != null && Objects.equals(node.status(), 2)).count();
        long terminatedCount = nodes.stream().filter(node -> node != null && Objects.equals(node.status(), 3)).count();
        long notStartedCount = nodes.stream().filter(node -> node != null && Objects.equals(node.status(), 0)).count();
        int total = nodes.size();

        if (activeCount == 1) return new State("IN_PROGRESS", null, new ArrayList<>(warnings));
        if (activeCount > 1) {
            warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                    "流程存在多个进行中节点"));
            return new State("INCONSISTENT", null, new ArrayList<>(warnings));
        }
        if (total > 0 && completedCount == total) {
            return new State("COMPLETED", null, new ArrayList<>(warnings));
        }
        if (terminatedCount > 0) {
            warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                    "节点已终止但流程没有对应终态"));
            warnings.add(noActive());
            return new State("INCONSISTENT", null, new ArrayList<>(warnings));
        }
        if (completedCount > 0) {
            warnings.add(new AiWorkflowContextDTO.Warning(WARNING_INCONSISTENT,
                    "部分节点已完成但没有进行中的节点"));
            warnings.add(noActive());
            return new State("INCONSISTENT", null, new ArrayList<>(warnings));
        }
        // 到这里剩下全部 status=0。
        return new State("NOT_STARTED", null, new ArrayList<>(warnings));
    }

    private AiWorkflowContextDTO.Warning noActive() {
        return new AiWorkflowContextDTO.Warning(WARNING_NO_ACTIVE_NODE,
                "流程没有进行中的节点");
    }

    private boolean nodesMissingUnknown(List<NodeRef> nodes) {
        return nodes.stream().anyMatch(node ->
                node == null || node.id() == null || node.status() == null
                        || node.status() < 0 || node.status() > 3);
    }

    private String normalizeInstanceTerminal(String terminalStatus) {
        return "REJECTED".equals(terminalStatus) ? "REJECTED" : null;
    }

    private String notConfiguredTerminal(boolean projectLike, boolean requirementLike, String entityStatus) {
        if (projectLike) {
            LifeCycle lifeCycle = LifeCycle.of(entityStatus);
            return switch (lifeCycle) {
                case COMPLETED -> "COMPLETED";
                case TERMINATED -> "TERMINATED";
                default -> null;
            };
        }
        if (requirementLike && "REJECTED".equals(entityStatus)) return "REJECTED";
        return null;
    }

    private enum LifeCycle {
        ACTIVE, COMPLETED, TERMINATED, UNKNOWN;

        static LifeCycle of(String entityStatus) {
            if (entityStatus == null || entityStatus.isBlank()) return ACTIVE;
            return switch (entityStatus) {
                case "ACTIVE" -> ACTIVE;
                case "COMPLETED" -> COMPLETED;
                case "TERMINATED" -> TERMINATED;
                default -> UNKNOWN;
            };
        }
    }
}
