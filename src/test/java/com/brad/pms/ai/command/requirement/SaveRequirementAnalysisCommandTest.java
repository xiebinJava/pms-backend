package com.brad.pms.ai.command.requirement;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.dto.request.RequirementReceivingAnalysisSaveCmd;
import com.brad.pms.dto.response.RequirementReceivingAnalysisDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.RequirementReceivingAnalysisService;
import com.brad.pms.workflow.RequirementReceivingAnalysisState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaveRequirementAnalysisCommandTest {

    @Mock RequirementReceivingAnalysisService analysisService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void previewsOnlyTheAnalysisPatchAndRefreshesRequirementDetail() {
        SaveRequirementAnalysisCommand command = command();

        CommandPreview preview = command.preview(new CommandPreviewRequest(
                CommandName.REQUIREMENT_ANALYSIS_SAVE,
                Map.of(
                        "requirementId", 23L,
                        "nodeId", 323L,
                        "version", 1,
                        "patch", Map.of("category", "FUNCTIONAL", "interpretation", "明确范围")),
                "requirement:23", "1"));

        assertThat(preview.command()).isEqualTo(CommandName.REQUIREMENT_ANALYSIS_SAVE);
        assertThat(preview.refreshScopes()).containsExactly("requirement-detail");
        assertThat(preview.warnings()).contains("仅保存需求接收与分析组件成果，不自动推进流程节点");
        assertThat(preview.changes()).singleElement().satisfies(change ->
                assertThat(change).containsEntry("action", "analysis.save"));
    }

    @Test
    void executeMergesPatchWithoutDroppingExistingAnalysisFields() {
        RequirementReceivingAnalysisDTO current = new RequirementReceivingAnalysisDTO();
        current.setState(new RequirementReceivingAnalysisState(
                RequirementReceivingAnalysisState.Validity.VALID,
                List.of(RequirementReceivingAnalysisState.FilterReason.OTHER),
                "原始理解", "原始过滤说明",
                RequirementReceivingAnalysisState.Category.FUNCTIONAL,
                4, 3, 5, "原结论",
                RequirementReceivingAnalysisState.Decision.PASS,
                "原补充", "原决策理由"));
        when(analysisService.get(23L, 323L)).thenReturn(current);
        when(analysisService.save(eq(23L), eq(323L), any(RequirementReceivingAnalysisSaveCmd.class)))
                .thenReturn(current);

        CommandResult result = command().execute(operation("""
                {"requirementId":23,"nodeId":323,"version":1,
                 "patch":{"analysisConclusion":"新结论","decisionReason":"新理由"}}
                """));

        ArgumentCaptor<RequirementReceivingAnalysisSaveCmd> captor =
                ArgumentCaptor.forClass(RequirementReceivingAnalysisSaveCmd.class);
        verify(analysisService).save(eq(23L), eq(323L), captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
        assertThat(captor.getValue().getState().get("validity").asText()).isEqualTo("VALID");
        assertThat(captor.getValue().getState().get("interpretation").asText()).isEqualTo("原始理解");
        assertThat(captor.getValue().getState().get("analysisConclusion").asText()).isEqualTo("新结论");
        assertThat(captor.getValue().getState().get("decisionReason").asText()).isEqualTo("新理由");
        assertThat(result.refreshScopes()).containsExactly("requirement-detail");
    }

    private SaveRequirementAnalysisCommand command() {
        return new SaveRequirementAnalysisCommand(
                analysisService, new PmsCommandSupport(objectMapper), objectMapper);
    }

    private static AiOperationDO operation(String arguments) {
        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-analysis-1");
        operation.setArgumentsJson(arguments);
        return operation;
    }
}
