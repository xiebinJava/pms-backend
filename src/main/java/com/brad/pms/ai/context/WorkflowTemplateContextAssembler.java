package com.brad.pms.ai.context;

import com.brad.pms.dto.response.WorkflowTemplateOptionsDTO;
import com.brad.pms.service.WorkflowTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkflowTemplateContextAssembler implements PageContextAssembler {

    private final WorkflowTemplateService workflowTemplateService;

    @Override
    public PageContextType supports() {
        return PageContextType.WORKFLOW_TEMPLATE;
    }

    @Override
    public PageContextSnapshot assemble(PageContextRequest request) {
        WorkflowTemplateOptionsDTO options = workflowTemplateService.options();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("options", options);
        data.put("selectedTemplateId", request.pageState().get("templateId"));
        return ContextSnapshotFactory.create(request, "workflow-template", "options", data);
    }
}
