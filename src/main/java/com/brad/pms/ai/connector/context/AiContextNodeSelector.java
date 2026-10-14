package com.brad.pms.ai.connector.context;

import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure selection logic over actual workflow nodes: derives the unique current node and the
 * explicitly-or-default selected node without any persistence access. Explicit node ids that
 * do not belong to the node list are rejected as 404.
 */
@org.springframework.stereotype.Component
public final class AiContextNodeSelector {

    private static final String WARNING_NO_ACTIVE_NODE = "NO_ACTIVE_NODE";
    private static final String WARNING_AMBIGUOUS_ACTIVE_NODES = "AMBIGUOUS_ACTIVE_NODES";

    public record Selection(Long currentNodeId, Long selectedNodeId, List<AiWorkflowContextDTO.Warning> warnings) {
        public Selection {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    /** Selects the current/unique active node, or the explicitly requested node when given. */
    public Selection select(List<NodeRef> nodes, Long requestedNodeId) {
        Set<AiWorkflowContextDTO.Warning> warnings = new LinkedHashSet<>();
        List<NodeRef> safeNodes = nodes == null ? List.of() : nodes;
        List<Long> activeIds = safeNodes.stream()
                .filter(node -> node != null && Objects.equals(node.status(), 1))
                .map(NodeRef::id)
                .filter(Objects::nonNull)
                .toList();

        if (activeIds.size() > 1) {
            warnings.add(new AiWorkflowContextDTO.Warning(WARNING_AMBIGUOUS_ACTIVE_NODES,
                    "流程存在多个进行中节点，current 与默认 selected 均为空"));
        } else if (activeIds.isEmpty()) {
            warnings.add(new AiWorkflowContextDTO.Warning(WARNING_NO_ACTIVE_NODE,
                    "流程没有进行中的节点"));
        }

        Long current = activeIds.size() == 1 ? activeIds.get(0) : null;
        Long selected;
        if (requestedNodeId == null) {
            selected = current;
        } else {
            boolean exists = safeNodes.stream().anyMatch(node -> node != null
                    && Objects.equals(node.id(), requestedNodeId));
            if (!exists) throw com.brad.pms.common.exception.BusinessException.notFound("节点不存在或不属于该流程");
            selected = requestedNodeId;
        }
        return new Selection(current, selected, new ArrayList<>(warnings));
    }
}
