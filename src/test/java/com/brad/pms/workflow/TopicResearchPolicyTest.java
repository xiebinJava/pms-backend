package com.brad.pms.workflow;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TopicResearchPolicyTest {
    @Test void templateWorkbenchIsTopicOnly() {
        var node = new WorkflowNodeDefinition("research", "需求调研", "", "", "", java.util.List.of("topic-research"), java.util.List.of(), false, java.util.List.of());
        var definition = new WorkflowTemplateDefinition(1, java.util.List.of(node));
        assertDoesNotThrow(() -> WorkflowTemplateDefinitionValidator.validateForProcessType("topic-management", definition));
        for (String type : java.util.List.of("general", "requirement-management", "story-management")) {
            assertThrows(IllegalArgumentException.class, () -> WorkflowTemplateDefinitionValidator.validateForProcessType(type, definition));
        }
    }
    @Test void requiresExplicitChoice() {
        assertThrows(IllegalArgumentException.class, () -> TopicResearchPolicy.validate(Map.of()));
    }
    @Test void skippingNeedsReasonNotReport() {
        assertThrows(IllegalArgumentException.class, () -> TopicResearchPolicy.validate(Map.of("needed", "NO")));
        assertDoesNotThrow(() -> TopicResearchPolicy.validate(Map.of("needed", "NO", "skipReason", "已有调研结论")));
    }
    @Test void researchNeedsGoalAndDocumentLink() {
        assertThrows(IllegalArgumentException.class, () -> TopicResearchPolicy.validate(Map.of("needed", "YES", "report", "报告")));
        assertThrows(IllegalArgumentException.class, () -> TopicResearchPolicy.validate(Map.of("needed", "YES", "goal", "比较竞品")));
        assertDoesNotThrow(() -> TopicResearchPolicy.validate(Map.of("needed", "YES", "goal", "比较竞品", "reportUrl", "https://docs.example.com/report")));
        assertThrows(IllegalArgumentException.class, () -> TopicResearchPolicy.validate(Map.of("needed", "YES", "goal", "比较竞品", "report", "旧正文")));
    }
    @Test void rejectsTextAndUnsafeLinksAsReports() {
        for (String url : java.util.List.of("报告", "javascript:alert(1)", "file:///report", "https://", "https://bad link")) {
            assertThrows(IllegalArgumentException.class, () -> TopicResearchPolicy.validate(Map.of("needed", "YES", "goal", "比较竞品", "reportUrl", url)));
        }
    }
    @Test void clientAttachmentMetadataCannotBypassValidation() {
        assertThrows(IllegalArgumentException.class, () -> TopicResearchPolicy.validate(Map.of("needed", "YES", "goal", "目标", "attachments", java.util.List.of("fake"))));
    }
}
