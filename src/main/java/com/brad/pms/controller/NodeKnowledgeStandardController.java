package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.NodeKnowledgeStandardUpdateCmd;
import com.brad.pms.dto.response.NodeKnowledgeStandardDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.NodeKnowledgeStandardService;
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
@RequestMapping("/projects/{projectId}/nodes/{nodeId}/knowledge-standard")
@RequiredArgsConstructor
public class NodeKnowledgeStandardController {

    private final NodeKnowledgeStandardService service;

    @GetMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeKnowledgeStandardDTO> get(@PathVariable Long projectId, @PathVariable Long nodeId) {
        return ResponseResult.success(service.get(projectId, nodeId));
    }

    @PutMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeKnowledgeStandardDTO> save(@PathVariable Long projectId,
                                                         @PathVariable Long nodeId,
                                                         @Validated @Valid @RequestBody NodeKnowledgeStandardUpdateCmd cmd) {
        return ResponseResult.success(service.save(projectId, nodeId, cmd));
    }
}
