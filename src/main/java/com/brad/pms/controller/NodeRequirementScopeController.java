package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.NodeRequirementScopeUpdateCmd;
import com.brad.pms.dto.response.NodeRequirementScopeDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.NodeRequirementScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/nodes/{nodeId}/requirements-scope")
@RequiredArgsConstructor
public class NodeRequirementScopeController {

    private final NodeRequirementScopeService service;

    @GetMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeRequirementScopeDTO> get(@PathVariable Long projectId, @PathVariable Long nodeId) {
        return ResponseResult.success(service.get(projectId, nodeId));
    }

    @PutMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeRequirementScopeDTO> save(@PathVariable Long projectId,
                                                         @PathVariable Long nodeId,
                                                         @Validated @Valid @RequestBody NodeRequirementScopeUpdateCmd cmd) {
        return ResponseResult.success(service.saveDraft(projectId, nodeId, cmd));
    }

    @PostMapping("/confirm")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeRequirementScopeDTO> confirm(@PathVariable Long projectId, @PathVariable Long nodeId) {
        return ResponseResult.success(service.confirm(projectId, nodeId));
    }

}
