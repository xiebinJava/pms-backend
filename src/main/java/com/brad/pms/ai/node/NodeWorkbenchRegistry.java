package com.brad.pms.ai.node;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeAcceptanceUpdateCmd;
import com.brad.pms.dto.request.NodeDevelopmentControlUpdateCmd;
import com.brad.pms.dto.request.NodeKnowledgeStandardUpdateCmd;
import com.brad.pms.dto.request.NodePlanResourceRiskUpdateCmd;
import com.brad.pms.dto.request.NodeReleaseUpdateCmd;
import com.brad.pms.dto.request.NodeRequirementScopeUpdateCmd;
import com.brad.pms.dto.request.NodeSolutionPackageUpdateCmd;
import com.brad.pms.dto.request.NodeSolutionDecisionUpdateCmd;
import com.brad.pms.dto.request.NodeValueReviewUpdateCmd;
import com.brad.pms.service.NodeAcceptanceService;
import com.brad.pms.service.NodeCustomFieldService;
import com.brad.pms.service.NodeDevelopmentControlService;
import com.brad.pms.service.NodeKnowledgeStandardService;
import com.brad.pms.service.NodePlanResourceRiskService;
import com.brad.pms.service.NodeReleaseService;
import com.brad.pms.service.NodeRequirementScopeService;
import com.brad.pms.service.NodeSolutionDesignService;
import com.brad.pms.service.NodeValueReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single table that maps a workbench key to its PMS service. Adding a
 * workbench to the Agent surface is one row here plus its labels; adding a field
 * to an existing workbench needs neither.
 */
@Component
public class NodeWorkbenchRegistry {

    private final Map<String, NodeWorkbench> workbenches;

    public NodeWorkbenchRegistry(NodeRequirementScopeService requirementScope,
                                 NodeSolutionDesignService solutionDesign,
                                 NodePlanResourceRiskService planResourceRisk,
                                 NodeDevelopmentControlService developmentControl,
                                 NodeAcceptanceService acceptance,
                                 NodeReleaseService release,
                                 NodeValueReviewService valueReview,
                                 NodeKnowledgeStandardService knowledgeStandard,
                                 NodeCustomFieldService customField,
                                 ObjectMapper objectMapper,
                                 NodeFieldLabels labels) {
        Map<String, NodeWorkbench> table = new LinkedHashMap<>();
        table.put("requirement-scope", new TypedNodeWorkbench(
                "requirement-scope", NodeRequirementScopeUpdateCmd.class,
                requirementScope::get,
                (projectId, nodeId, command) -> requirementScope.saveDraft(
                        projectId, nodeId, (NodeRequirementScopeUpdateCmd) command),
                objectMapper, labels));
        table.put("solution-design", new TypedNodeWorkbench(
                "solution-design", NodeSolutionPackageUpdateCmd.class,
                "solutionPackage", solutionDesign::get,
                (projectId, nodeId, command) -> solutionDesign.saveDraft(
                        projectId, nodeId, (NodeSolutionPackageUpdateCmd) command),
                objectMapper, labels));
        table.put("solution-decision", new TypedNodeWorkbench(
                "solution-decision", NodeSolutionDecisionUpdateCmd.class,
                "decision", solutionDesign::get,
                (projectId, nodeId, command) -> solutionDesign.saveDecisionDraft(
                        projectId, nodeId, (NodeSolutionDecisionUpdateCmd) command),
                objectMapper, labels));
        table.put("plan-resource-risk", new TypedNodeWorkbench(
                "plan-resource-risk", NodePlanResourceRiskUpdateCmd.class,
                planResourceRisk::get,
                (projectId, nodeId, command) -> planResourceRisk.saveDraft(
                        projectId, nodeId, (NodePlanResourceRiskUpdateCmd) command),
                objectMapper, labels));
        table.put("development-control", new TypedNodeWorkbench(
                "development-control", NodeDevelopmentControlUpdateCmd.class,
                developmentControl::get,
                (projectId, nodeId, command) -> developmentControl.save(
                        projectId, nodeId, (NodeDevelopmentControlUpdateCmd) command),
                objectMapper, labels));
        table.put("business-acceptance", new TypedNodeWorkbench(
                "business-acceptance", NodeAcceptanceUpdateCmd.class,
                acceptance::get,
                (projectId, nodeId, command) -> acceptance.saveDraft(
                        projectId, nodeId, (NodeAcceptanceUpdateCmd) command),
                objectMapper, labels));
        table.put("release-handover", new TypedNodeWorkbench(
                "release-handover", NodeReleaseUpdateCmd.class,
                release::get,
                (projectId, nodeId, command) -> release.save(
                        projectId, nodeId, (NodeReleaseUpdateCmd) command),
                objectMapper, labels));
        table.put("value-review", new TypedNodeWorkbench(
                "value-review", NodeValueReviewUpdateCmd.class,
                valueReview::get,
                (projectId, nodeId, command) -> valueReview.save(
                        projectId, nodeId, (NodeValueReviewUpdateCmd) command),
                objectMapper, labels));
        table.put("knowledge-standard", new TypedNodeWorkbench(
                "knowledge-standard", NodeKnowledgeStandardUpdateCmd.class,
                knowledgeStandard::get,
                (projectId, nodeId, command) -> knowledgeStandard.save(
                        projectId, nodeId, (NodeKnowledgeStandardUpdateCmd) command),
                objectMapper, labels));
        table.put(CustomFieldNodeWorkbench.KEY, new CustomFieldNodeWorkbench(customField, objectMapper));
        this.workbenches = Map.copyOf(table);
    }

    public NodeWorkbench require(String key) {
        if (key == null || key.isBlank()) throw BusinessException.error("缺少工作台标识");
        NodeWorkbench workbench = workbenches.get(key.trim());
        if (workbench == null) {
            throw BusinessException.error("不支持的工作台: " + key + "；可用工作台：" + String.join("、", workbenches.keySet()));
        }
        return workbench;
    }

    public Set<String> keys() {
        return workbenches.keySet();
    }

    public List<NodeWorkbench> list() {
        return List.copyOf(workbenches.values());
    }
}
