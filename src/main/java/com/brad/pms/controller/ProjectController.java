package com.brad.pms.controller;

import com.brad.pms.common.page.PageResult;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.ProjectCreateCmd;
import com.brad.pms.dto.request.ProjectPageQry;
import com.brad.pms.dto.request.ProjectUpdateCmd;
import com.brad.pms.dto.request.ProjectLifecycleCmd;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.service.ProjectService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<ProjectDTO> create(@Validated @RequestBody ProjectCreateCmd cmd) {
        return ResponseResult.success(projectService.create(cmd));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<ProjectDTO> update(@PathVariable Long id, @Validated @RequestBody ProjectUpdateCmd cmd) {
        return ResponseResult.success(projectService.update(id, cmd));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseResult.success();
    }

    @PostMapping("/{id}/terminate")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<ProjectDTO> terminate(@PathVariable Long id,
                                                @Validated @RequestBody ProjectLifecycleCmd cmd) {
        return ResponseResult.success(projectService.terminate(id, cmd.getReason()));
    }

    @PostMapping("/{id}/restore")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<ProjectDTO> restore(@PathVariable Long id,
                                              @Validated @RequestBody ProjectLifecycleCmd cmd) {
        return ResponseResult.success(projectService.restore(id, cmd.getReason()));
    }

    @GetMapping("/stats")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<java.util.Map<String, Object>> stats() {
        return ResponseResult.success(projectService.stats());
    }

    @PostMapping("/page")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<PageResult<ProjectDTO>> page(@RequestBody ProjectPageQry qry) {
        return ResponseResult.success(projectService.page(qry));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<ProjectDTO> detail(@PathVariable Long id) {
        return ResponseResult.success(projectService.detail(id));
    }
}
