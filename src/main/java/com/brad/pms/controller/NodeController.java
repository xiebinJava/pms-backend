package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public ResponseResult<List<ProjectNodeDTO>> rollback(@PathVariable Long projectId, @PathVariable Long nodeId) {
        return ResponseResult.success(nodeService.rollback(projectId, nodeId));
    }
}
