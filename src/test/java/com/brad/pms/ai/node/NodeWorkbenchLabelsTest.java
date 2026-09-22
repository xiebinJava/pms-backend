package com.brad.pms.ai.node;

import com.brad.pms.dto.request.NodeAcceptanceUpdateCmd;
import com.brad.pms.dto.request.NodeDevelopmentControlUpdateCmd;
import com.brad.pms.dto.request.NodeKnowledgeStandardUpdateCmd;
import com.brad.pms.dto.request.NodePlanResourceRiskUpdateCmd;
import com.brad.pms.dto.request.NodeReleaseUpdateCmd;
import com.brad.pms.dto.request.NodeRequirementScopeUpdateCmd;
import com.brad.pms.dto.request.NodeSolutionPackageUpdateCmd;
import com.brad.pms.dto.request.NodeSolutionDecisionUpdateCmd;
import com.brad.pms.dto.request.NodeValueReviewUpdateCmd;
import com.brad.pms.dto.response.NodeAcceptanceDTO;
import com.brad.pms.dto.response.NodeDevelopmentControlDTO;
import com.brad.pms.dto.response.NodeKnowledgeStandardDTO;
import com.brad.pms.dto.response.NodePlanResourceRiskDTO;
import com.brad.pms.dto.response.NodeReleaseDTO;
import com.brad.pms.dto.response.NodeRequirementScopeDTO;
import com.brad.pms.dto.response.NodeSolutionDesignDTO;
import com.brad.pms.dto.response.NodeValueReviewDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Agent previews changes by Chinese label. This test fails when a workbench
 * gains a field that `agent-labels/pms/node-field-labels.yaml` does not name, so
 * a new field can never silently reach users as a raw English key.
 */
class NodeWorkbenchLabelsTest {

    private static final Map<String, Class<?>> TYPED_WORKBENCHES = Map.of(
            "requirement-scope", NodeRequirementScopeUpdateCmd.class,
            "solution-design", NodeSolutionPackageUpdateCmd.class,
            "solution-decision", NodeSolutionDecisionUpdateCmd.class,
            "plan-resource-risk", NodePlanResourceRiskUpdateCmd.class,
            "development-control", NodeDevelopmentControlUpdateCmd.class,
            "business-acceptance", NodeAcceptanceUpdateCmd.class,
            "release-handover", NodeReleaseUpdateCmd.class,
            "value-review", NodeValueReviewUpdateCmd.class,
            "knowledge-standard", NodeKnowledgeStandardUpdateCmd.class);

    /**
     * Workbench key to (update command, read response, nested document path). The
     * read response must expose every field the update command accepts, otherwise
     * a partial update would silently clear the fields it cannot read back.
     */
    private static final List<WorkbenchContract> CONTRACTS = List.of(
            new WorkbenchContract("requirement-scope", NodeRequirementScopeUpdateCmd.class,
                    NodeRequirementScopeDTO.class, null),
            new WorkbenchContract("solution-design", NodeSolutionPackageUpdateCmd.class,
                    NodeSolutionDesignDTO.class, "solutionPackage"),
            new WorkbenchContract("solution-decision", NodeSolutionDecisionUpdateCmd.class,
                    NodeSolutionDesignDTO.class, "decision"),
            new WorkbenchContract("plan-resource-risk", NodePlanResourceRiskUpdateCmd.class,
                    NodePlanResourceRiskDTO.class, null),
            new WorkbenchContract("development-control", NodeDevelopmentControlUpdateCmd.class,
                    NodeDevelopmentControlDTO.class, null),
            new WorkbenchContract("business-acceptance", NodeAcceptanceUpdateCmd.class,
                    NodeAcceptanceDTO.class, null),
            new WorkbenchContract("release-handover", NodeReleaseUpdateCmd.class,
                    NodeReleaseDTO.class, null),
            new WorkbenchContract("value-review", NodeValueReviewUpdateCmd.class,
                    NodeValueReviewDTO.class, null),
            new WorkbenchContract("knowledge-standard", NodeKnowledgeStandardUpdateCmd.class,
                    NodeKnowledgeStandardDTO.class, null));

    @Test
    void everyWritableWorkbenchFieldHasAChineseLabel() {
        NodeFieldLabels labels = new NodeFieldLabels();
        ObjectMapper mapper = new ObjectMapper();
        for (Map.Entry<String, Class<?>> entry : TYPED_WORKBENCHES.entrySet()) {
            TypedNodeWorkbench workbench = new TypedNodeWorkbench(
                    entry.getKey(), entry.getValue(), (projectId, nodeId) -> null,
                    (projectId, nodeId, command) -> null, mapper, labels);

            assertThat(workbench.writableFields()).isNotEmpty();
            for (String field : workbench.writableFields()) {
                assertThat(labels.knows(entry.getKey(), field))
                        .as(entry.getKey() + "." + field + " 缺少中文标签")
                        .isTrue();
            }
        }
    }

    @Test
    void labelsFileCoversEveryWorkbenchKeyTheAgentPublishes() {
        NodeFieldLabels labels = new NodeFieldLabels();

        for (String key : TYPED_WORKBENCHES.keySet()) {
            assertThat(labels.workbench(key)).as(key + " 工作台名称").isNotEqualTo(key);
        }
        assertThat(labels.workbench(CustomFieldNodeWorkbench.KEY)).isEqualTo("节点自定义字段");
    }

    @Test
    void everyWritableFieldCanBeReadBackFromTheCurrentDocument() {
        for (WorkbenchContract contract : CONTRACTS) {
            Set<String> writable = fieldsOf(contract.commandType());
            writable.remove("version");
            Set<String> readable = fieldsOf(nestedType(contract));

            assertThat(readable)
                    .as(contract.key() + " 无法读回的字段（部分更新会清空它们）")
                    .containsAll(writable);
        }
    }

    private static Class<?> nestedType(WorkbenchContract contract) {
        if (contract.documentPath() == null) return contract.documentType();
        return fieldType(contract.documentType(), contract.documentPath());
    }

    private static Class<?> fieldType(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(name)) return field.getType();
            }
        }
        throw new IllegalStateException(type.getSimpleName() + " 没有字段 " + name);
    }

    private static Set<String> fieldsOf(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                names.add(field.getName());
            }
        }
        return names;
    }

    private record WorkbenchContract(String key, Class<?> commandType, Class<?> documentType, String documentPath) {
    }
}
