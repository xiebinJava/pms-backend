package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.NodeValueReviewUpdateCmd;
import com.brad.pms.dto.response.NodeValueReviewDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.NodeValueReviewService;
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
@RequestMapping("/projects/{projectId}/nodes/{nodeId}/value-review")
@RequiredArgsConstructor
public class NodeValueReviewController {

    private final NodeValueReviewService service;

    @GetMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeValueReviewDTO> get(@PathVariable Long projectId, @PathVariable Long nodeId) {
        return ResponseResult.success(service.get(projectId, nodeId));
    }

    @PutMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeValueReviewDTO> save(@PathVariable Long projectId,
                                                    @PathVariable Long nodeId,
                                                    @Validated @Valid @RequestBody NodeValueReviewUpdateCmd cmd) {
        return ResponseResult.success(service.save(projectId, nodeId, cmd));
    }
}
