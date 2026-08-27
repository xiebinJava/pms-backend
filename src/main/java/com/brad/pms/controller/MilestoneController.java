package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.MilestoneCreateCmd;
import com.brad.pms.dto.request.MilestoneUpdateCmd;
import com.brad.pms.dto.response.ProjectMilestoneDTO;
import com.brad.pms.service.MilestoneService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/milestones")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    @GetMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<ProjectMilestoneDTO>> list(@PathVariable Long projectId) {
        return ResponseResult.success(milestoneService.listByProject(projectId));
    }

    @PostMapping
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<ProjectMilestoneDTO> create(@PathVariable Long projectId,
                                                      @Validated @RequestBody MilestoneCreateCmd cmd) {
        return ResponseResult.success(milestoneService.create(projectId, cmd));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<ProjectMilestoneDTO> update(@PathVariable Long id,
                                                      @Validated @RequestBody MilestoneUpdateCmd cmd) {
        return ResponseResult.success(milestoneService.update(id, cmd));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Void> delete(@PathVariable Long id) {
        milestoneService.delete(id);
        return ResponseResult.success();
    }
}
