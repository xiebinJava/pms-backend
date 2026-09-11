package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.NodeOwnerUpdateCmd;
import com.brad.pms.dto.request.NodeRollbackCmd;
import com.brad.pms.dto.request.NodeScheduleUpdateCmd;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.service.NodeService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;

    @GetMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<ProjectNodeDTO>> list(@PathVariable Long projectId) {
        return ResponseResult.success(nodeService.list(projectId));
    }

    @PostMapping("/{nodeId}/complete")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<ProjectNodeDTO>> complete(@PathVariable Long projectId, @PathVariable Long nodeId) {
        return ResponseResult.success(nodeService.complete(projectId, nodeId));
    }

    @PostMapping("/{nodeId}/rollback")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<List<ProjectNodeDTO>> rollback(@PathVariable Long projectId,
                                                         @PathVariable Long nodeId,
                                                         @Validated @RequestBody NodeRollbackCmd cmd) {
        return ResponseResult.success(nodeService.rollback(projectId, nodeId, cmd.getReason()));
    }

    @PutMapping("/{nodeId}/owner")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<ProjectNodeDTO> updateOwner(@PathVariable Long projectId,
                                                       @PathVariable Long nodeId,
                                                       @Validated @RequestBody NodeOwnerUpdateCmd cmd) {
        return ResponseResult.success(nodeService.updateOwner(projectId, nodeId, cmd));
    }

    @PutMapping("/{nodeId}/schedule")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<ProjectNodeDTO> updateSchedule(@PathVariable Long projectId,
                                                          @PathVariable Long nodeId,
                                                          @Validated @RequestBody NodeScheduleUpdateCmd cmd) {
        return ResponseResult.success(nodeService.updateSchedule(projectId, nodeId, cmd));
    }
}
