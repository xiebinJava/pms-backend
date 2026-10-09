package com.brad.pms.workflow;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TopicDesignReviewPolicyTest {
    private Map<String, Object> valid() {
        return new HashMap<>(Map.of("productPlanUrl", "https://example.com/plan", "productReviewerIds", List.of(1L),
                "productReviewStatus", "PASSED"));
    }
    @Test void optionalDesignAndTechnicalReviewsMayBeEmpty() {
        assertDoesNotThrow(() -> TopicDesignReviewPolicy.validate(valid()));
    }
    @Test void requiredFieldsCannotBeOmitted() {
        for (String key : valid().keySet()) {
            var state = valid(); state.remove(key);
            assertThrows(IllegalArgumentException.class, () -> TopicDesignReviewPolicy.validate(state), key);
        }
    }
    @Test void rejectedReviewCannotComplete() {
        var state = valid(); state.put("productReviewStatus", "REJECTED");
        assertThrows(IllegalArgumentException.class, () -> TopicDesignReviewPolicy.validate(state));
    }
    @Test void removedFinalResultFieldsDoNotBlockCompletion() {
        var state = valid(); state.put("decision", "REJECTED"); state.put("finalOpinion", "旧意见");
        assertDoesNotThrow(() -> TopicDesignReviewPolicy.validate(state));
    }
    @Test void documentReferencesOnlyNeedContentNotAProtocol() {
        for (String key : List.of("productPlanUrl", "uiPlanUrl", "technicalPlanUrl")) {
            var state = valid(); state.put(key, "内部文档/方案一");
            assertDoesNotThrow(() -> TopicDesignReviewPolicy.validate(state));
        }
        var state = valid(); state.put("productPlanUrl", "  ");
        assertThrows(IllegalArgumentException.class, () -> TopicDesignReviewPolicy.validate(state));
    }
    @Test void reviewersMustBePositiveIntegralIds() {
        for (Object ids : List.of(List.of(-1), List.of(1.5), "1")) {
            var state = valid(); state.put("productReviewerIds", ids);
            assertThrows(IllegalArgumentException.class, () -> TopicDesignReviewPolicy.validate(state));
        }
    }
    @Test void workbenchIsTopicOnly() {
        var node = new WorkflowNodeDefinition("review", "方案设计与评审", "", "", "", List.of("topic-design-review"), List.of(), false, List.of());
        var definition = new WorkflowTemplateDefinition(1, List.of(node));
        assertDoesNotThrow(() -> WorkflowTemplateDefinitionValidator.validateForProcessType("topic-management", definition));
        for (String type : List.of("general", "requirement-management", "story-management"))
            assertThrows(IllegalArgumentException.class, () -> WorkflowTemplateDefinitionValidator.validateForProcessType(type, definition));
    }
}
