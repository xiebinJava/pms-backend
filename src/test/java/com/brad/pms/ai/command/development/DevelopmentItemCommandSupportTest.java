package com.brad.pms.ai.command.development;

import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowFieldType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DevelopmentItemCommandSupportTest {
    @Test
    void declaresOnlySubmittedServerBoundEntityMutations() {
        var node = new DevelopmentItemWorkflowNodeDTO();
        node.setFields(List.of(
                new WorkflowFieldDefinition("owner", "负责人", WorkflowFieldType.PERSON, false, List.of(), true, "requirement.owner"),
                new WorkflowFieldDefinition("title", "名称", WorkflowFieldType.TEXT, true, List.of(), true, "requirement.title")));
        var mapper = new ObjectMapper();
        assertThat(DevelopmentItemCommandSupport.boundMutations(node, Map.of("owner", mapper.valueToTree(2))))
                .containsExactly("ownerId");
        assertThat(DevelopmentItemCommandSupport.boundMutations(node, Map.of("status", mapper.valueToTree("DONE"))))
                .isEmpty();
    }
}
