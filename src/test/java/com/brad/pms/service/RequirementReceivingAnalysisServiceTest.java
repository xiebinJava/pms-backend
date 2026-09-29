package com.brad.pms.service;

import com.brad.pms.audit.AuditAction;
import com.brad.pms.dto.request.RequirementReceivingAnalysisActionCmd;
import com.brad.pms.dto.request.RequirementReceivingAnalysisSaveCmd;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.workflow.RequirementReceivingAnalysisState;
import com.brad.pms.workflow.WorkflowComponentKey;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequirementReceivingAnalysisServiceTest {
    private final RequirementMapper requirementMapper = mock(RequirementMapper.class);
    private final DevelopmentItemWorkflowMapper workflowMapper = mock(DevelopmentItemWorkflowMapper.class);
    private final DevelopmentItemWorkflowNodeMapper nodeMapper = mock(DevelopmentItemWorkflowNodeMapper.class);
    private final WorkflowTemplateService workflowTemplateService = mock(WorkflowTemplateService.class);
    private final OperationLogService operationLogService = mock(OperationLogService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RequirementReceivingAnalysisService service = new RequirementReceivingAnalysisService(
            requirementMapper, workflowMapper, nodeMapper, workflowTemplateService, operationLogService, objectMapper);

    private RequirementDO requirement;
    private DevelopmentItemWorkflowDO workflow;
    private DevelopmentItemWorkflowNodeDO receivingNode;
    private WorkflowTemplateDefinition definition;

    @BeforeEach
    void setUp() {
        requirement = new RequirementDO();
        requirement.setId(11L);
        requirement.setStatus("ACTIVE");
        requirement.setDeleted(false);
        requirement.setVersion(0);

        workflow = new DevelopmentItemWorkflowDO();
        workflow.setId(101L);
        workflow.setItemType("REQUIREMENT");
        workflow.setItemId(11L);
        workflow.setTemplateVersionId(201L);
        workflow.setVersion(0);

        receivingNode = node("receive", "需求接收", 1, 1);
        receivingNode.setFieldValuesJson("{\"unrelated\":\"keep\"}");
        definition = new WorkflowTemplateDefinition(2, List.of(new WorkflowNodeDefinition(
                "receive", "需求接收", "", "", "", List.of(), List.of(), false, List.of(),
                List.of("component:" + WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS),
                Map.of(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS, objectMapper.createObjectNode()))));

        when(requirementMapper.selectById(11L)).thenReturn(requirement);
        when(requirementMapper.selectByIdForUpdate(11L)).thenReturn(requirement);
        when(workflowMapper.selectByItem("REQUIREMENT", 11L)).thenReturn(workflow);
        when(workflowMapper.selectForUpdate("REQUIREMENT", 11L)).thenReturn(workflow);
        when(nodeMapper.selectById(301L)).thenReturn(receivingNode);
        when(nodeMapper.selectByWorkflowIdsForUpdate(any())).thenReturn(List.of(receivingNode));
        when(nodeMapper.updateById(any(DevelopmentItemWorkflowNodeDO.class))).thenReturn(1);
        when(workflowMapper.updateById(any(DevelopmentItemWorkflowDO.class))).thenReturn(1);
        when(requirementMapper.updateById(any(RequirementDO.class))).thenReturn(1);
        when(workflowTemplateService.getDefinition(201L)).thenReturn(definition);
    }

    @Test
    void savesComponentStateWithoutDroppingUnrelatedNodeValues() throws Exception {
        RequirementReceivingAnalysisSaveCmd cmd = new RequirementReceivingAnalysisSaveCmd();
        cmd.setVersion(1);
        cmd.setState(stateNode(RequirementReceivingAnalysisState.Validity.VALID,
                RequirementReceivingAnalysisState.Decision.PASS));

        var saved = service.save(11L, 301L, cmd);

        assertThat(saved.getState().decision()).isEqualTo(RequirementReceivingAnalysisState.Decision.PASS);
        assertThat(objectMapper.readTree(receivingNode.getFieldValuesJson()).path("unrelated").asText())
                .isEqualTo("keep");
        assertThat(objectMapper.readTree(receivingNode.getFieldValuesJson()).path("__components")
                .path(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS).path("validity").asText())
                .isEqualTo("VALID");
        verify(operationLogService).record(any());
    }

    @Test
    void rejectsRequirementAndTerminatesItsWorkflow() {
        receivingNode.setFieldValuesJson(objectMapper.valueToTree(Map.of(
                "__components", Map.of(WorkflowComponentKey.REQUIREMENT_RECEIVING_ANALYSIS,
                        stateNode(RequirementReceivingAnalysisState.Validity.INVALID,
                                RequirementReceivingAnalysisState.Decision.REJECT)))
        ).toString());
        RequirementReceivingAnalysisActionCmd cmd = new RequirementReceivingAnalysisActionCmd();
        cmd.setReason("重复需求，无需继续处理");

        service.reject(11L, 301L, cmd);

        assertThat(requirement.getStatus()).isEqualTo("REJECTED");
        assertThat(workflow.getTerminalStatus()).isEqualTo("REJECTED");
        verify(operationLogService).record(any());
    }

    @Test
    void reopensRejectedRequirementAndRestoresReceivingNode() {
        requirement.setStatus("REJECTED");
        workflow.setTerminalStatus("REJECTED");
        receivingNode.setStatus(2);
        DevelopmentItemWorkflowNodeDO later = node("later", "后续节点", 2, 0);
        when(nodeMapper.selectByWorkflowIdsForUpdate(any())).thenReturn(List.of(receivingNode, later));
        when(workflowTemplateService.getDefinition(201L)).thenReturn(new WorkflowTemplateDefinition(2, List.of(
                definition.nodes().get(0), new WorkflowNodeDefinition(
                        "later", "后续节点", "", "", "", List.of(), List.of(), false, List.of(), List.of(), Map.of()))));
        RequirementReceivingAnalysisActionCmd cmd = new RequirementReceivingAnalysisActionCmd();
        cmd.setReason("补充信息后重新评估");

        service.reopen(11L, cmd);

        assertThat(requirement.getStatus()).isEqualTo("ACTIVE");
        assertThat(workflow.getTerminalStatus()).isNull();
        assertThat(receivingNode.getStatus()).isEqualTo(1);
        assertThat(later.getStatus()).isZero();
        verify(operationLogService).record(any());
    }

    @Test
    void doesNotAllowSavingAgainstANewerNodeVersion() {
        receivingNode.setVersion(2);
        RequirementReceivingAnalysisSaveCmd cmd = new RequirementReceivingAnalysisSaveCmd();
        cmd.setVersion(1);
        cmd.setState(stateNode(RequirementReceivingAnalysisState.Validity.VALID,
                RequirementReceivingAnalysisState.Decision.PASS));

        assertThatThrownBy(() -> service.save(11L, 301L, cmd))
                .hasMessageContaining("流程节点已被其他人修改");
    }

    private DevelopmentItemWorkflowNodeDO node(String key, String name, int sort, int status) {
        DevelopmentItemWorkflowNodeDO node = new DevelopmentItemWorkflowNodeDO();
        node.setId(key.equals("receive") ? 301L : 302L);
        node.setWorkflowId(101L);
        node.setNodeKey(key);
        node.setName(name);
        node.setSort(sort);
        node.setStatus(status);
        node.setVersion(1);
        node.setOwnerId(9L);
        node.setStartDate(LocalDate.of(2026, 9, 28));
        node.setEndDate(LocalDate.of(2026, 9, 29));
        return node;
    }

    private JsonNode stateNode(RequirementReceivingAnalysisState.Validity validity,
                               RequirementReceivingAnalysisState.Decision decision) {
        return objectMapper.valueToTree(new RequirementReceivingAnalysisState(validity,
                List.of(RequirementReceivingAnalysisState.FilterReason.OTHER), "已完成需求解释",
                "其他原因说明", RequirementReceivingAnalysisState.Category.FUNCTIONAL,
                4, 4, 4, "具备较高价值", decision, "", decision == RequirementReceivingAnalysisState.Decision.REJECT ? "需要驳回" : ""));
    }
}
