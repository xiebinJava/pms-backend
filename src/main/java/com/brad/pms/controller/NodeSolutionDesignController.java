package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.NodeSolutionDecisionConfirmCmd;
import com.brad.pms.dto.request.NodeSolutionPackageUpdateCmd;
import com.brad.pms.dto.request.NodeSolutionReviewCompleteCmd;
import com.brad.pms.dto.response.NodeSolutionDesignDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.NodeSolutionDesignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/nodes/{nodeId}/solution-design")
@RequiredArgsConstructor
public class NodeSolutionDesignController {

    private final NodeSolutionDesignService service;

    @GetMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeSolutionDesignDTO> get(@PathVariable Long projectId, @PathVariable Long nodeId) {
        return ResponseResult.success(service.get(projectId, nodeId));
    }

    @PutMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeSolutionDesignDTO> save(@PathVariable Long projectId,
                                                       @PathVariable Long nodeId,
                                                       @Valid @RequestBody NodeSolutionPackageUpdateCmd cmd) {
        return ResponseResult.success(service.saveDraft(projectId, nodeId, cmd));
    }

    @PostMapping("/submit")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeSolutionDesignDTO> submit(@PathVariable Long projectId,
                                                         @PathVariable Long nodeId,
                                                         @RequestBody(required = false) NodeSolutionPackageUpdateCmd cmd) {
        return ResponseResult.success(service.submitPackage(projectId, nodeId, cmd == null ? null : cmd.getVersion()));
    }

    @PostMapping("/reviews/{reviewType}/complete")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeSolutionDesignDTO> completeReview(@PathVariable Long projectId,
                                                                  @PathVariable Long nodeId,
                                                                  @PathVariable String reviewType,
                                                                  @Valid @RequestBody(required = false) NodeSolutionReviewCompleteCmd cmd) {
        return ResponseResult.success(service.completeReview(projectId, nodeId, reviewType, cmd == null ? null : cmd.getComment()));
    }

    @PostMapping("/decision/confirm")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeSolutionDesignDTO> confirmDecision(@PathVariable Long projectId,
                                                                  @PathVariable Long nodeId,
                                                                  @Valid @RequestBody NodeSolutionDecisionConfirmCmd cmd) {
        return ResponseResult.success(service.confirmDecision(projectId, nodeId, cmd));
    }

    @PostMapping("/decision/reopen")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<NodeSolutionDesignDTO> reopenDecision(@PathVariable Long projectId,
                                                                 @PathVariable Long nodeId) {
        return ResponseResult.success(service.reopenDecision(projectId, nodeId));
    }
}
