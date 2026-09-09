package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.NodeReleaseUpdateCmd;
import com.brad.pms.dto.response.NodeReleaseDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.NodeReleaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/nodes/{nodeId}/release")
@RequiredArgsConstructor
public class NodeReleaseController {

    private final NodeReleaseService service;

    @GetMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeReleaseDTO> get(@PathVariable Long projectId, @PathVariable Long nodeId) {
        return ResponseResult.success(service.get(projectId, nodeId));
    }

    @PutMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeReleaseDTO> save(@PathVariable Long projectId,
                                                @PathVariable Long nodeId,
                                                @Validated @Valid @RequestBody NodeReleaseUpdateCmd cmd) {
        return ResponseResult.success(service.save(projectId, nodeId, cmd));
    }
}
