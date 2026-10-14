package com.brad.pms.ai.connector.context;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Positioning contract for the AI readonly workflow context. */
class AiContextNodeSelectorTest {

    private final AiContextNodeSelector selector = new AiContextNodeSelector();

    @Test
    void theUniqueActiveNodeIsBothCurrentAndDefaultSelected() {
        AiContextNodeSelector.Selection selection = selector.select(
                List.of(new NodeRef(299L, 0, 0), new NodeRef(301L, 1, 1)), null);

        assertThat(selection.currentNodeId()).isEqualTo(301L);
        assertThat(selection.selectedNodeId()).isEqualTo(301L);
        assertThat(selection.warnings()).isEmpty();
    }

    @Test
    void anExplicitHistoryNodeIsSelectedWithoutChangingTheCurrentFact() {
        AiContextNodeSelector.Selection selection = selector.select(
                List.of(new NodeRef(299L, 2, 0), new NodeRef(301L, 1, 1)), 299L);

        assertThat(selection.currentNodeId()).isEqualTo(301L);
        assertThat(selection.selectedNodeId()).isEqualTo(299L);
        assertThat(selection.warnings()).isEmpty();
    }

    @Test
    void zeroActiveNodesReportNoActiveInsteadOfPickingTheFirstNode() {
        AiContextNodeSelector.Selection selection = selector.select(
                List.of(new NodeRef(299L, 0, 0), new NodeRef(301L, 0, 1)), null);

        assertThat(selection.currentNodeId()).isNull();
        assertThat(selection.selectedNodeId()).isNull();
        assertThat(selection.warnings()).extracting(AiWorkflowContextDTO.Warning::code)
                .containsExactly("NO_ACTIVE_NODE");
    }

    @Test
    void multipleActiveNodesReportAmbiguityAndKeepBothSelectionsNull() {
        AiContextNodeSelector.Selection selection = selector.select(
                List.of(new NodeRef(299L, 1, 0), new NodeRef(301L, 1, 1)), null);

        assertThat(selection.currentNodeId()).isNull();
        assertThat(selection.selectedNodeId()).isNull();
        assertThat(selection.warnings()).extracting(AiWorkflowContextDTO.Warning::code)
                .containsExactly("AMBIGUOUS_ACTIVE_NODES");
    }

    @Test
    void explicitHistorySelectionStillWorksWhileNodesAreAmbiguous() {
        AiContextNodeSelector.Selection selection = selector.select(
                List.of(new NodeRef(299L, 1, 0), new NodeRef(301L, 1, 1), new NodeRef(310L, 2, 2)), 310L);

        assertThat(selection.currentNodeId()).isNull();
        assertThat(selection.selectedNodeId()).isEqualTo(310L);
        assertThat(selection.warnings()).extracting(AiWorkflowContextDTO.Warning::code)
                .containsExactly("AMBIGUOUS_ACTIVE_NODES");
    }

    @Test
    void anExplicitNodeOutsideTheWorkflowIsRejected() {
        assertThatThrownBy(() -> selector.select(
                List.of(new NodeRef(299L, 1, 0)), 999L))
                .isInstanceOf(BusinessException.class)
                .isInstanceOfSatisfying(BusinessException.class, thrown ->
                        assertThat(thrown.getCode()).isEqualTo(404));
    }

    @Test
    void nullListsAreTreatedAsEmpty() {
        AiContextNodeSelector.Selection selection = selector.select(null, null);

        assertThat(selection.currentNodeId()).isNull();
        assertThat(selection.selectedNodeId()).isNull();
        assertThat(selection.warnings()).extracting(AiWorkflowContextDTO.Warning::code)
                .containsExactly("NO_ACTIVE_NODE");
        assertThat(selection.warnings()).allSatisfy(warning ->
                assertThat(warning.message()).isNotBlank());
    }
}
