package com.brad.pms.ai.connector.context;

import com.brad.pms.common.exception.BusinessException;
import java.util.List;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The transactional pure-read facade normalizes options and gates the focused budget. */
@ExtendWith(MockitoExtension.class)
class AiWorkflowContextReadServiceTest {

    @Mock AiProjectContextReader projectReader;
    @Mock AiDevelopmentContextReader developmentReader;
    @Mock AiContextResponseBudget budget;

    private AiWorkflowContextReadService service() {
        return new AiWorkflowContextReadService(projectReader, developmentReader, budget);
    }

    @Test
    void legacyOptionsAreTheDefaultWhenCallersPassNull() {
        when(projectReader.read(7L, ContextReadOptions.legacy())).thenReturn(context());

        service().context("project", 7L, null);

        verify(projectReader).read(7L, ContextReadOptions.legacy());
        verify(budget).requireWithinFocusedLimit(context());
    }

    @Test
    void projectTopicStoryAndRequirementRouteToTheirReaders() {
        when(projectReader.read(7L, ContextReadOptions.legacy())).thenReturn(context());
        when(developmentReader.read(com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L,
                ContextReadOptions.legacy())).thenReturn(context());
        when(developmentReader.read(com.brad.pms.workflow.DevelopmentItemType.TOPIC, 8L,
                ContextReadOptions.legacy())).thenReturn(context());
        when(developmentReader.read(com.brad.pms.workflow.DevelopmentItemType.STORY, 9L,
                ContextReadOptions.legacy())).thenReturn(context());

        service().context("project", 7L, ContextReadOptions.legacy());
        service().context("requirement", 23L, ContextReadOptions.legacy());
        service().context("topic", 8L, ContextReadOptions.legacy());
        service().context("story", 9L, ContextReadOptions.legacy());

        verify(developmentReader).read(com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT,
                23L, ContextReadOptions.legacy());
        verify(developmentReader).read(com.brad.pms.workflow.DevelopmentItemType.TOPIC,
                8L, ContextReadOptions.legacy());
        verify(developmentReader).read(com.brad.pms.workflow.DevelopmentItemType.STORY,
                9L, ContextReadOptions.legacy());
    }

    @Test
    void unknownTypesAndMissingIdsAreRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> service().context("iteration_plan", 5L, ContextReadOptions.legacy()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不支持的流程上下文资源类型");
        assertThatThrownBy(() -> service().context("project", null, ContextReadOptions.legacy()))
                .isInstanceOf(BusinessException.class);
        verify(projectReader, never()).read(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aMissingReaderResultIsNeverPassedThroughTheBudgetAsSuccess() {
        when(projectReader.read(7L, ContextReadOptions.legacy())).thenThrow(
                new BusinessException("无权读取该类型对象"));

        assertThatThrownBy(() -> service().context("project", 7L, ContextReadOptions.legacy()))
                .isInstanceOf(BusinessException.class);
        verify(budget, never()).requireWithinFocusedLimit(org.mockito.ArgumentMatchers.any());
    }

    private AiWorkflowContextDTO context() {
        return new AiWorkflowContextDTO("project", 7L, 3, null, null, List.of(), null, null, null, null);
    }
}
