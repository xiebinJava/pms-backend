package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.ProjectTypeSaveCmd;
import com.brad.pms.dto.request.WorkflowTemplateDefaultCmd;
import com.brad.pms.dto.request.WorkflowTemplateSaveCmd;
import com.brad.pms.dto.response.ProjectTypeDTO;
import com.brad.pms.dto.response.WorkflowTemplateDTO;
import com.brad.pms.dto.response.WorkflowTemplateSummaryDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.WorkflowTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/workflow-config")
@RequiredArgsConstructor
public class AdminWorkflowConfigController {
    private final WorkflowTemplateService workflowTemplateService;

    @GetMapping("/project-types")
    @RequirePermission(PermissionCode.WORKFLOW_READ)
    public ResponseResult<List<ProjectTypeDTO>> listProjectTypes() {
        return ResponseResult.success(workflowTemplateService.listProjectTypes());
    }

    @PostMapping("/project-types")
    @RequirePermission(PermissionCode.WORKFLOW_WRITE)
    public ResponseResult<ProjectTypeDTO> createProjectType(@Valid @RequestBody ProjectTypeSaveCmd cmd) {
        return ResponseResult.success(workflowTemplateService.createProjectType(cmd));
    }

    @PutMapping("/project-types/{id}/default-template")
    @RequirePermission(PermissionCode.WORKFLOW_WRITE)
    public ResponseResult<ProjectTypeDTO> setDefault(@PathVariable Long id,
                                                       @Valid @RequestBody WorkflowTemplateDefaultCmd cmd) {
        return ResponseResult.success(workflowTemplateService.setDefaultTemplate(id, cmd.getTemplateVersionId()));
    }

    @GetMapping("/templates")
    @RequirePermission(PermissionCode.WORKFLOW_READ)
    public ResponseResult<List<WorkflowTemplateSummaryDTO>> listTemplates(
            @RequestParam(required = false) Long projectTypeId) {
        return ResponseResult.success(workflowTemplateService.listTemplates(projectTypeId, false));
    }

    @GetMapping("/templates/{id}")
    @RequirePermission(PermissionCode.WORKFLOW_READ)
    public ResponseResult<WorkflowTemplateDTO> getTemplate(@PathVariable Long id) {
        return ResponseResult.success(workflowTemplateService.getTemplate(id));
    }

    @PostMapping("/templates")
    @RequirePermission(PermissionCode.WORKFLOW_WRITE)
    public ResponseResult<WorkflowTemplateDTO> createTemplate(@Valid @RequestBody WorkflowTemplateSaveCmd cmd) {
        return ResponseResult.success(workflowTemplateService.saveDraft(null, cmd));
    }

    @PutMapping("/templates/{id}/draft")
    @RequirePermission(PermissionCode.WORKFLOW_WRITE)
    public ResponseResult<WorkflowTemplateDTO> saveDraft(@PathVariable Long id,
                                                           @Valid @RequestBody WorkflowTemplateSaveCmd cmd) {
        return ResponseResult.success(workflowTemplateService.saveDraft(id, cmd));
    }

    @PostMapping("/templates/{id}/publish")
    @RequirePermission(PermissionCode.WORKFLOW_WRITE)
    public ResponseResult<WorkflowTemplateDTO> publish(@PathVariable Long id) {
        return ResponseResult.success(workflowTemplateService.publish(id));
    }
}
