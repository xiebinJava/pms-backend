package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.response.WorkflowTemplateOptionsDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.WorkflowTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow-templates")
@RequiredArgsConstructor
public class WorkflowTemplateOptionsController {
    private final WorkflowTemplateService workflowTemplateService;

    @GetMapping("/options")
    @RequirePermission(PermissionCode.PROJECT_CREATE)
    public ResponseResult<WorkflowTemplateOptionsDTO> options() {
        return ResponseResult.success(workflowTemplateService.options());
    }
}
