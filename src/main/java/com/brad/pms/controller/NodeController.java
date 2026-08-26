package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.NodeOwnerUpdateCmd;
import com.brad.pms.dto.request.NodeRollbackCmd;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.service.NodeService;
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
    public ResponseResult<List<ProjectNodeDTO>> list(@PathVariable Long projectId) {
        return ResponseResult.success(nodeService.list(projectId));
    }

    @PostMapping("/{nodeId}/complete")
    public ResponseResult<List<ProjectNodeDTO>> complete(@PathVariable Long projectId, @PathVariable Long nodeId) {
        return ResponseResult.success(nodeService.complete(projectId, nodeId));
    }

    @PostMapping("/{nodeId}/rollback")
    public ResponseResult<List<ProjectNodeDTO>> rollback(@PathVariable Long projectId,
                                                         @PathVariable Long nodeId,
                                                         @Validated @RequestBody NodeRollbackCmd cmd) {
        return ResponseResult.success(nodeService.rollback(projectId, nodeId, cmd.getReason()));
    }

    @PutMapping("/{nodeId}/owner")
    public ResponseResult<ProjectNodeDTO> updateOwner(@PathVariable Long projectId,
                                                       @PathVariable Long nodeId,
                                                       @RequestBody NodeOwnerUpdateCmd cmd) {
        return ResponseResult.success(nodeService.updateOwner(projectId, nodeId, cmd.getOwnerId()));
    }
}
